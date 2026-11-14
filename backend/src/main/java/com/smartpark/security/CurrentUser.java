package com.smartpark.security;

public record CurrentUser(Long id, String email, UserRole role) {
}
