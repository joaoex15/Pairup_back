package com.example.Tinder_ufs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public void logSecurityViolation(String userId, String action) {
        log.error("SECURITY VIOLATION - Usuário {} tentou: {} em {}",
                userId, action, LocalDateTime.now());
    }

    public void logImageAccess(String userId, String imageId, String action) {
        log.info("AUDIT - Usuário {} acessou imagem {} para {} em {}",
                userId, imageId, action, LocalDateTime.now());
    }
}
