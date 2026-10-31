package com.petcare.grooming;

import com.petcare.grooming.media.PhotoStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product_photos;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@AutoConfigureMockMvc
class ProductPhotoTest {
    static final Path DIRECTORY = Path.of("target", "test-product-photos", UUID.randomUUID().toString()).toAbsolutePath();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) { registry.add("pet-grooming.media-directory", DIRECTORY::toString); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;

    private MockMultipartFile form(String name) {
        return new MockMultipartFile("product", "product.json", "application/json", ("{\"name\":\"" + name + "\",\"unit\":\"ml\",\"threshold\":10}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private MockMultipartFile photo() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("image", "../../photo.png", "image/png", output.toByteArray());
    }
    private int products() { return db.queryForObject("select count(*) from product", Integer.class); }
    private long files() throws Exception {
        if (!Files.exists(DIRECTORY)) return 0;
        try (var paths = Files.walk(DIRECTORY)) { return paths.filter(Files::isRegularFile).count(); }
    }

    @Test void createsProductAndItsOwnPhotoTogetherAndKeepsTheImageAfterRestart() throws Exception {
        var image = photo();
        String body = mvc.perform(multipart("/api/inventory").file(form("独立照片耗材")).file(image).with(user("manager").roles("MANAGER")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(0))
            .andExpect(jsonPath("$.data.image_key", matchesPattern("[0-9a-f-]{36}\\.png"))).andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.data.image_key");
        int id = JsonPath.read(body, "$.data.id");
        assertEquals(key, db.queryForObject("select image_key from product where id=?", String.class, id));
        var response = mvc.perform(get("/api/media/products/" + key + "/image")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/png")).andExpect(content().bytes(image.getBytes()))
            .andReturn().getResponse();
        mvc.perform(get("/api/media/products/" + key + "/image").header("If-None-Match", response.getHeader("ETag")))
            .andExpect(status().isNotModified());
        assertArrayEquals(image.getBytes(), new PhotoStorageService(DIRECTORY.toString()).productImage(key).bytes());
        assertNull(db.queryForObject("select image_key from product where id=1", String.class));
    }
    @Test void jsonCreationWithoutPhotoRemainsSupported() throws Exception {
        mvc.perform(post("/api/inventory").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"不带照片耗材\",\"unit\":\"件\",\"threshold\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.image_key").value(nullValue()));
    }
    @Test void invalidPhotoDoesNotCreateAProduct() throws Exception {
        int before = products();
        mvc.perform(multipart("/api/inventory").file(form("坏照片耗材"))
                .file(new MockMultipartFile("image", "fake.png", "image/png", "<svg/>".getBytes()))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
        assertEquals(before, products());
    }
    @Test void invalidProductDoesNotSaveItsPhoto() throws Exception {
        int before = products();
        long filesBefore = files();
        mvc.perform(multipart("/api/inventory").file(form("")).file(photo()).with(user("manager").roles("MANAGER")).with(csrf()))
            .andExpect(status().isBadRequest());
        assertEquals(before, products());
        assertEquals(filesBefore, files());
    }
    @Test void supportsJpegPhotos() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        String body = mvc.perform(multipart("/api/inventory").file(form("JPEG耗材"))
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", output.toByteArray()))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.image_key", endsWith(".jpg")))
            .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.data.image_key");
        mvc.perform(get("/api/media/products/" + key + "/image")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/jpeg"));
    }
    @Test void rollingBackCreationRemovesTheUploadedFile() throws Exception {
        int before = products();
        long filesBefore = files();
        String key = new TransactionTemplate(transactions).execute(transaction -> {
            try {
                String body = mvc.perform(multipart("/api/inventory").file(form("回滚耗材")).file(photo())
                        .with(user("manager").roles("MANAGER")).with(csrf()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                transaction.setRollbackOnly();
                return JsonPath.read(body, "$.data.image_key");
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        assertEquals(before, products());
        assertEquals(filesBefore, files());
        mvc.perform(get("/api/media/products/" + key + "/image")).andExpect(status().isNotFound());
    }
    @Test void enforcesUploadLimitsAndExistingCreationPermissions() throws Exception {
        int before = products();
        mvc.perform(multipart("/api/inventory").file(form("大图片耗材"))
                .file(new MockMultipartFile("image", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                .with(user("manager").roles("MANAGER")).with(csrf())).andExpect(status().isPayloadTooLarge());
        for (String role : new String[]{"OWNER", "GROOMER", "RECEPTION"}) {
            String username = role.equals("OWNER") ? "owner" : role.equals("GROOMER") ? "groomer" : "reception";
            mvc.perform(multipart("/api/inventory").file(form("无权限耗材")).file(photo()).with(user(username).roles(role)).with(csrf()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/inventory").file(form("缺少令牌耗材")).file(photo()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
        assertEquals(before, products());
    }
    @Test void unknownImageKeysCannotReadArbitraryFiles() throws Exception {
        mvc.perform(get("/api/media/products/not-a-key/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/media/products/" + UUID.randomUUID() + ".png/image")).andExpect(status().isNotFound());
    }
}
