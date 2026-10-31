package com.petcare.grooming.media;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;

@RestController
public class ProductPhotoController {
    private final PhotoStorageService photos;
    public ProductPhotoController(PhotoStorageService photos) { this.photos = photos; }
    @GetMapping("/api/media/products/{key}/image")
    public ResponseEntity<byte[]> image(@PathVariable String key, WebRequest request) {
        var image = photos.productImage(key);
        String etag = "\"" + image.version() + "\"";
        if (request.checkNotModified(etag)) return null;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).eTag(etag)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).body(image.bytes());
    }
    @GetMapping("/api/media/pets/{id}/image")
    public ResponseEntity<byte[]> petImage(@PathVariable long id, WebRequest request) {
        var image = photos.petImage(id);
        String etag = "\"" + image.version() + "\"";
        if (request.checkNotModified(etag)) return null;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).eTag(etag)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(image.bytes());
    }
}
