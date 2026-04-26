package com.aiinterviewer.admin.common.security;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtService(
            @Value("${admin.jwt.secret}") String secret,
            @Value("${admin.jwt.access-token-expiration}") long accessTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(Long adminUserId, List<String> roles) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(adminUserId))
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (AdminBusinessException exception) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public List<String> getRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (!(roles instanceof List<?> roleValues)) {
            return List.of();
        }
        return roleValues.stream().map(String::valueOf).toList();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new AdminBusinessException(401, "Token已过期", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AdminBusinessException(401, "Token无效", exception);
        }
    }
}
