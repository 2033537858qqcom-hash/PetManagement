package com.petcare.grooming;

import com.petcare.grooming.media.PhotoStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:photo_management;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class PhotoManagementTest {
    static final Path DIRECTORY = Path.of("target", "test-photos", UUID.randomUUID().toString()).toAbsolutePath();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("pet-grooming.media-directory", DIRECTORY::toString);
    }
    @Autowired MockMvc mvc;

    private byte[] image(String format, int color) throws Exception {
        var picture = new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB);
        picture.setRGB(0, 0, color);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(picture, format, output));
        return output.toByteArray();
    }
    private MockMultipartFile png() throws Exception { return new MockMultipartFile("file", "photo.png", "image/png", image("png", 0x558877)); }

    @Test void catalogueIsPublicAndContainsTheExistingProjectPhotos() throws Exception {
        mvc.perform(get("/api/media/photos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(13)))
            .andExpect(jsonPath("$.data[?(@.key=='entry-cover')].defaultUrl", contains("/images/entry-cover.jpg")))
            .andExpect(jsonPath("$.data[*].category", hasItems("品牌封面", "宠物照片", "洗护项目", "商品图片", "会员展示")));
    }
    @Test void adminCanUploadAndPubliclyReadAValidatedImage() throws Exception {
        byte[] bytes = image("png", 0x123456);
        var file = new MockMultipartFile("file", "../../outside.png", "image/png", bytes);
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/product-shampoo").file(file).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customized").value(true))
            .andExpect(jsonPath("$.data.url", startsWith("/api/media/photos/product-shampoo/image?v=")));
        var response = mvc.perform(get("/api/media/photos/product-shampoo/image")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/png")).andExpect(content().bytes(bytes))
            .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse();
        mvc.perform(get("/api/media/photos/product-shampoo/image").header("If-None-Match", response.getHeader("ETag")))
            .andExpect(status().isNotModified());
        assertFalse(java.nio.file.Files.exists(DIRECTORY.getParent().resolve("outside.png")));
    }
    @Test void jpegUploadsSurviveRecreatingTheStorageService() throws Exception {
        byte[] bytes = image("jpeg", 0x887766);
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/care-bath")
                .file(new MockMultipartFile("file", "bath.jpg", "image/jpeg", bytes)).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        var restarted = new PhotoStorageService(DIRECTORY.toString());
        assertTrue(restarted.catalogue().stream().anyMatch(photo -> photo.key().equals("care-bath") && photo.customized()));
        assertArrayEquals(bytes, restarted.image("care-bath").bytes());
        assertEquals("image/jpeg", restarted.image("care-bath").contentType());
    }
    @Test void replacementChangesTheVersionAndResetRestoresTheDefault() throws Exception {
        var upload = multipart(HttpMethod.PUT, "/api/media/photos/care-spa").file(png()).with(user("admin").roles("ADMIN")).with(csrf());
        mvc.perform(upload).andExpect(status().isOk());
        String first = mvc.perform(get("/api/media/photos/care-spa/image")).andReturn().getResponse().getHeader("ETag");
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/care-spa")
                .file(new MockMultipartFile("file", "spa.png", "image/png", image("png", 0xabcdef))).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        String second = mvc.perform(get("/api/media/photos/care-spa/image")).andReturn().getResponse().getHeader("ETag");
        assertNotEquals(first, second);
        mvc.perform(delete("/api/media/photos/care-spa").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customized").value(false))
            .andExpect(jsonPath("$.data.url").value("/images/care-spa.jpg"));
        mvc.perform(get("/api/media/photos/care-spa/image")).andExpect(status().isNotFound());
    }
    @Test void onlyAdministratorsCanChangeOrResetPhotos() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover").file(png()).with(csrf())).andExpect(status().isUnauthorized());
        for (String role : new String[]{"MANAGER", "RECEPTION", "GROOMER", "OWNER"}) {
            String username = switch (role) { case "MANAGER" -> "manager"; case "RECEPTION" -> "reception"; case "GROOMER" -> "groomer"; default -> "owner"; };
            mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover").file(png()).with(user(username).roles(role)).with(csrf()))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/media/photos/entry-cover").with(user(username).roles(role)).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover").file(png()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
    @Test void rejectsEmptyFakeAndUnsupportedImages() throws Exception {
        for (var file : new MockMultipartFile[]{
            new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
            new MockMultipartFile("file", "fake.png", "image/png", "<svg onload='alert(1)'/>".getBytes()),
            new MockMultipartFile("file", "photo.gif", "image/gif", image("gif", 0x112233)),
            new MockMultipartFile("file", "broken.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10})
        }) {
            mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover").file(file).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        }
    }
    @Test void enforcesTheUploadSizeAndDimensionLimits() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover")
                .file(new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isPayloadTooLarge());
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/entry-cover")
                .file(new MockMultipartFile("file", "wide.png", "image/png", output.toByteArray()))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void unknownPhotoKeysCannotWriteArbitraryFiles() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/media/photos/not-a-photo").file(png()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/media/photos/not-a-photo").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
}
