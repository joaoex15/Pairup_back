package com.example.pairup.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    static final String ISSUER = "pairup-api";
    private static final long EXPIRATION_MS = 15 * 60 * 1000L; // 15 minutos
    private static final long CLOCK_SKEW_SECONDS = 30;

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

    /**
     * Falha na subida da aplicação (e não na primeira requisição) se o segredo for fraco.
     * HS256 exige pelo menos 256 bits de chave.
     */
    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "[Segurança] jwt.secret não configurado ou muito curto (mínimo 32 bytes). "
                            + "Defina JWT_SECRET, ex.: openssl rand -base64 48");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRATION_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valida assinatura, expiração e emissor; devolve null se qualquer um falhar.
     * O parser do jjwt rejeita "alg: none" e tokens sem assinatura (parseClaimsJws).
     */
    private Claims parse(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(ISSUER)
                    .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rejeitado: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public String extractUserId(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    public boolean isTokenValid(String token) {
        return parse(token) != null;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (userDetails == null) return false;
        Claims claims = parse(token);
        return claims != null
                && claims.getSubject() != null
                && claims.getSubject().equals(userDetails.getUsername());
    }

    public Date getExpirationDate(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getExpiration();
    }
}
