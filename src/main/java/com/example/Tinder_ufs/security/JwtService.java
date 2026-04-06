package com.example.Tinder_ufs.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String SECRET;

    private static final long EXPIRATION_MS = 15 * 60 * 1000L; // 15 minutos
    private static final long REFRESH_THRESHOLD_MS = 5 * 60 * 1000L; // 5 minutos

    private Key getKey() {
        if (SECRET == null || SECRET.length() < 32) {
            log.error("JWT Secret não configurado ou muito curto. Tamanho atual: {}",
                    SECRET != null ? SECRET.length() : 0);
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
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        String token = Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();

        log.debug("Token gerado para userId: {}, expira em: {}", userId, expiration);
        return token;
    }

    /**
     * Extrai o userId do token
     */
    public String extractUserId(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.debug("Token vazio ou nulo");
                return null;
            }

            String userId = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();

            log.debug("UserId extraído do token: {}", userId);
            return userId;

        } catch (ExpiredJwtException e) {
            log.debug("Token expirado ao extrair userId");
            return null;
        } catch (MalformedJwtException e) {
            log.debug("Token mal formatado ao extrair userId");
            return null;
        } catch (SignatureException e) {
            log.debug("Assinatura inválida ao extrair userId");
            return null;
        } catch (IllegalArgumentException e) {
            log.debug("Argumento inválido ao extrair userId: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.debug("Erro JWT ao extrair userId: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Valida se o token é estruturalmente válido (não expirado, assinatura correta)
     */
    public boolean isTokenValid(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.debug("Token vazio ou nulo na validação");
                return false;
            }

            Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token);

            log.debug("Token válido estruturalmente");
            return true;

        } catch (ExpiredJwtException e) {
            log.debug("Token expirado");
            return false;
        } catch (MalformedJwtException e) {
            log.debug("Token mal formatado");
            return false;
        } catch (SignatureException e) {
            log.debug("Assinatura inválida");
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("Argumento inválido: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("Erro JWT: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Valida se o token é válido para o UserDetails específico
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            if (token == null || userDetails == null) {
                log.debug("Token ou UserDetails nulo na validação");
                return false;
            }

            String userId = extractUserId(token);
            if (userId == null) {
                log.debug("Não foi possível extrair userId do token");
                return false;
            }

            boolean isUserIdValid = userId.equals(userDetails.getUsername());
            boolean isTokenExpired = isTokenExpired(token);

            boolean isValid = isUserIdValid && !isTokenExpired;

            if (isValid) {
                log.debug("Token válido para usuário: {}", userId);
            } else {
                log.debug("Token inválido para usuário: {} (userIdMatch: {}, expired: {})",
                        userId, isUserIdValid, isTokenExpired);
            }

            return isValid;

        } catch (Exception e) {
            log.debug("Erro na validação do token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o token está expirado
     */
    private boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDate(token);
            if (expiration == null) {
                return true;
            }
            boolean expired = expiration.before(new Date());
            if (expired) {
                log.debug("Token expirado em: {}", expiration);
            }
            return expired;
        } catch (Exception e) {
            log.debug("Erro ao verificar expiração: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Obtém a data de expiração do token
     */
    public Date getExpirationDate(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return null;
            }

            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
        } catch (Exception e) {
            log.debug("Erro ao obter data de expiração: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifica se o token pode ser renovado (faltam menos de 5 minutos para expirar)
     */
    public boolean isTokenRefreshable(String token) {
        Date expiration = getExpirationDate(token);
        if (expiration == null) {
            log.debug("Token sem data de expiração, não pode ser renovado");
            return false;
        }

        long timeUntilExpiration = expiration.getTime() - System.currentTimeMillis();
        boolean refreshable = timeUntilExpiration > 0 && timeUntilExpiration < REFRESH_THRESHOLD_MS;

        if (refreshable) {
            log.debug("Token pode ser renovado. Expira em {} ms", timeUntilExpiration);
        } else {
            log.debug("Token não pode ser renovado. Tempo até expirar: {} ms", timeUntilExpiration);
        }

        return refreshable;
    }

    /**
     * Renova o token (gera um novo com nova expiração)
     */
    public String refreshToken(String oldToken) {
        if (!isTokenRefreshable(oldToken)) {
            log.warn("Tentativa de renovar token que não pode ser renovado");
            return null;
        }

        String userId = extractUserId(oldToken);
        if (userId == null) {
            log.warn("Não foi possível extrair userId do token para renovação");
            return null;
        }

        log.info("Renovando token para userId: {}", userId);
        return generateToken(userId);
    }
}