package com.smartdoc.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private String username;
    private String role;
    private long expiresIn;     // milliseconds — tells client when to refresh
}