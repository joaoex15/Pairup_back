package com.example.pairup.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }

    /** userId do JWT, ou 401 (antes era RuntimeException e virava 500). */
    public static String getUserIdOrThrow() {
        String userId = getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        return userId;
    }

    /** Remove quebras de linha de valores vindos do cliente antes de ir para o log (log forging). */
    public static String paraLog(String valor) {
        if (valor == null) return "null";
        String limpo = valor.replaceAll("[\\r\\n\\t]", "_");
        return limpo.length() > 100 ? limpo.substring(0, 100) + "..." : limpo;
    }
}
