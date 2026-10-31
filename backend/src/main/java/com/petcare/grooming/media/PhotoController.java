package com.petcare.grooming.media;

import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/media/photos")
public class PhotoController {
    private final PhotoStorageService photos;
    private final AccountAccess access;
    public PhotoController(PhotoStorageService photos, AccountAccess access) { this.photos = photos; this.access = access; }

    @GetMapping public ApiResponse<List<PhotoStorageService.Asset>> catalogue() { return ApiResponse.ok(photos.catalogue()); }
    @PutMapping(value = "/{key}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhotoStorageService.Asset> replace(@PathVariable String key, @RequestParam("file") MultipartFile file) {
        access.require(access.role("ADMIN"));
        return ApiResponse.ok(photos.replace(key, file));
    }
    @DeleteMapping("/{key}") public ApiResponse<PhotoStorageService.Asset> reset(@PathVariable String key) {
        access.require(access.role("ADMIN"));
        return ApiResponse.ok(photos.reset(key));
    }
    @GetMapping("/{key}/image") public ResponseEntity<byte[]> image(@PathVariable String key, WebRequest request) {
        var image = photos.image(key);
        String etag = "\"" + image.version() + "\"";
        if (request.checkNotModified(etag)) return null;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .cacheControl(CacheControl.noCache()).eTag(etag).body(image.bytes());
    }
}
