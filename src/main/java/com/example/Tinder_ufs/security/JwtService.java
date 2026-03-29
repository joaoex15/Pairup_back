package com.example.Tinder_ufs.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // Leia o segredo de uma variável de ambiente / application.properties
    // No Railway: defina JWT_SECRET como uma string aleatória de 64+ caracteres
    // Exemplo local em application.properties:  jwt.secret=${JWT_SECRET}
    @Value("${jwt.secret}")
    private String SECRET;

    // Expiração curta: 15 minutos (era 1 dia — muito longo)
    private static final long EXPIRATION_MS = 15 * 60 * 1000L;

    private Key getKey() {
        if (SECRET == null || SECRET.length() < 32) {
            throw new IllegalStateException(
                    "[Segurança] jwt.secret não configurado ou muito curto (mínimo 32 chars). " +
                            "Defina a variável de ambiente JWT_SECRET no Railway."
            );
        }
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    public String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}