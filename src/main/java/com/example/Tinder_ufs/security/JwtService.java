package com.example.Tinder_ufs.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String SECRET;

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
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (MalformedJwtException e) {
            return false;
        } catch (SignatureException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String userId = extractUserId(token);
            if (userId == null) {
                return false;
            }

            boolean isUserIdValid = userId.equals(userDetails.getUsername());
            boolean isTokenExpired = isTokenExpired(token);

            return isUserIdValid && !isTokenExpired;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public Date getExpirationDate(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenRefreshable(String token) {
        Date expiration = getExpirationDate(token);
        if (expiration == null) {
            return false;
        }

        long fiveMinutesInMs = 5 * 60 * 1000L;
        long timeUntilExpiration = expiration.getTime() - System.currentTimeMillis();

        return timeUntilExpiration > 0 && timeUntilExpiration < fiveMinutesInMs;
    }
}