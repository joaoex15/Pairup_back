package com.example.Tinder_ufs.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utilitário centralizado para extração e validação do userId do JWT.
 *
 * Por que centralizar?
 *  - O atributo "userId" é setado pelo JwtFilter. Se o nome mudar,
 *    corrige-se aqui e reflete em todos os controllers automaticamente.
 *  - Elimina ~15 blocos if(userId == null) repetidos nos controllers.
 *  - Garante que qualquer endpoint que precise de autenticação lança
 *    sempre o mesmo status/mensagem padronizados.
 */
public final class SecurityUtils {

    private static final String USER_ID_ATTRIBUTE = "userId";

    private SecurityUtils() {}

    /**
     * Extrai o userId do atributo setado pelo JwtFilter.
     * Lança 401 se o token estiver ausente ou inválido.
     *
     * @param request requisição HTTP atual
     * @return userId não-nulo extraído do JWT
     */
    public static String getUserIdOrThrow(HttpServletRequest request) {
        String userId = (String) request.getAttribute(USER_ID_ATTRIBUTE);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token ausente ou inválido."
            );
        }
        return userId;
    }
}