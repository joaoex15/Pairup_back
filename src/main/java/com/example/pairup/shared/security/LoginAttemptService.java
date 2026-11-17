package com.example.pairup.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio temporário por e-mail depois de várias senhas erradas seguidas.
 * Complementa o {@link RateLimitFilter} (por IP): impede força bruta distribuída em
 * vários IPs contra uma mesma conta.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_CHAVES = 100_000;

    private final int maxFalhas;
    private final long bloqueioMs;

    private record Estado(int falhas, long bloqueadoAte, long ultimaFalha) {}

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${seguranca.login.max-falhas:5}") int maxFalhas,
                               @Value("${seguranca.login.bloqueio-minutos:15}") long bloqueioMinutos) {
        this.maxFalhas = maxFalhas;
        this.bloqueioMs = bloqueioMinutos * 60_000L;
    }

    public boolean estaBloqueado(String email) {
        Estado e = estados.get(chave(email));
        return e != null && e.bloqueadoAte() > System.currentTimeMillis();
    }

    public void registrarFalha(String email) {
        long agora = System.currentTimeMillis();
        if (estados.size() > MAX_CHAVES) {
            estados.entrySet().removeIf(en -> agora - en.getValue().ultimaFalha() > bloqueioMs);
        }
        estados.compute(chave(email), (k, atual) -> {
            // Falhas antigas (fora da janela de bloqueio) não acumulam
            int falhas = (atual == null || agora - atual.ultimaFalha() > bloqueioMs) ? 1 : atual.falhas() + 1;
            long bloqueadoAte = falhas >= maxFalhas ? agora + bloqueioMs : 0L;
            return new Estado(falhas, bloqueadoAte, agora);
        });
    }

    public void registrarSucesso(String email) {
        estados.remove(chave(email));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
