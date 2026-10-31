package com.petcare.grooming.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(ApiResponse.error(413, "图片不能超过 5 MB"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ResponseStatusException exception) {
        int code = exception.getStatusCode().value();
        String message = exception.getReason();
        if (message == null || message.isBlank()) message = "请求失败";
        return ResponseEntity.status(code).body(ApiResponse.error(code, message));
    }
}
