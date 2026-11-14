package com.smartpark.security;

import com.smartpark.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${smartpark.jwt.secret}") String secret,
            @Value("${smartpark.jwt.expiration:PT30M}") Duration expiration) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "SMARTPARK_JWT_SECRET must contain at least 32 UTF-8 bytes"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String issueToken(User user) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(expiration)))
            .signWith(signingKey)
            .compact();
    }

    public long getUserId(String token) throws JwtException {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public Duration getExpiration() {
        return expiration;
    }
}
