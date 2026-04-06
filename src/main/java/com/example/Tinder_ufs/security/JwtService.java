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

    // Leia o segredo de uma variável de ambiente / application.properties
    // No Railway: defina JWT_SECRET como uma string aleatória de 64+ caracteres
    // Exemplo local em application.properties: jwt.secret=${JWT_SECRET}
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

    /**
     * Gera um token JWT para o userId
     */
    public String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extrai o userId do token
     */
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

    /**
     * Valida se o token é válido (não expirado, assinatura correta)
     * ✅ CORRIGIDO: Retorna false em vez de lançar exceção
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Token expirado
            return false;
        } catch (MalformedJwtException e) {
            // Token mal formatado
            return false;
        } catch (SignatureException e) {
            // Assinatura inválida
            return false;
        } catch (IllegalArgumentException e) {
            // Token vazio ou null
            return false;
        } catch (JwtException e) {
            // Qualquer outro erro JWT
            return false;
        }
    }

    /**
     * Valida o token para um UserDetails específico
     * ✅ CORRIGIDO: Verifica se o userId do token corresponde ao do UserDetails
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String userId = extractUserId(token);
            if (userId == null) {
                return false;
            }

            // Verifica se o userId do token corresponde ao username do UserDetails
            boolean isUserIdValid = userId.equals(userDetails.getUsername());

            // Verifica se o token não está expirado
            boolean isTokenExpired = isTokenExpired(token);

            return isUserIdValid && !isTokenExpired;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica se o token está expirado
     */
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

    /**
     * Obtém a data de expiração do token
     */
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

    /**
     * Verifica se o token pode ser renovado (ex: menos de 5 min para expirar)
     */
    public boolean isTokenRefreshable(String token) {
        Date expiration = getExpirationDate(token);
        if (expiration == null) {
            return false;
        }

        // Se falta menos de 5 minutos para expirar, pode renovar
        long fiveMinutesInMs = 5 * 60 * 1000L;
        long timeUntilExpiration = expiration.getTime() - System.currentTimeMillis();

        return timeUntilExpiration > 0 && timeUntilExpiration < fiveMinutesInMs;
    }
}