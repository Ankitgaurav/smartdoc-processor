package com.smartdoc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.Instant;

/**
 * Standard wrapper for ALL API responses.
 *
 * Every endpoint returns this — never raw objects.
 * This gives clients a consistent contract to code against.
 *
 * Example success: {"success":true,"data":{...},"message":"OK","timestamp":"..."}
 * Example error:   {"success":false,"message":"Not found","timestamp":"..."}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)   // don't serialize null fields
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    // ── Static factory methods (cleaner than calling builder every time) ──

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true).message(message).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false).message(message).build();
    }
}