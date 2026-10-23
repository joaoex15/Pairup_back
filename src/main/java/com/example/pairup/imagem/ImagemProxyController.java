package com.example.pairup.imagem;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.shared.security.SecurityUtils;
import com.example.pairup.match.MatchService;
import com.example.pairup.pessoa.PessoaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/imagens/proxy")
@RequiredArgsConstructor
public class ImagemProxyController {

    private final ImagemService imagemService;
    private final PessoaService pessoaService;
    private final MatchService matchService;
    private final AuditLogService auditLogService;

    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9_/\\-]{3,150}\\.[a-z]{2,5}$"
    );

    @GetMapping("/**")
    public ResponseEntity<Void> proxyImagemHandler(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String publicId = fullPath.replace("/api/imagens/proxy/", "");

        log.info("Proxy request - publicId extraído: {}", publicId);

        if (isPathTraversal(publicId)) {
            log.warn("[SECURITY] Path traversal bloqueado: {}", publicId);
            auditLogService.logSecurityViolation(
                    SecurityUtils.getCurrentUserId() != null ? SecurityUtils.getCurrentUserId() : "unknown",
                    "Path traversal attempt: " + publicId
            );
            return ResponseEntity.badRequest().build();
        }

        return proxyImagem(publicId);
    }

    private ResponseEntity<Void> proxyImagem(String publicId) {
        try {
            String userId = SecurityUtils.getUserIdOrThrow();

            if (!isValidPublicId(publicId)) {
                log.warn("[SECURITY] PublicId inválido: {} from user: {}", publicId, userId);
                return ResponseEntity.badRequest().build();
            }

            Imagem imagem;
            try {
                imagem = imagemService.findByPublicId(publicId);
                log.info("Imagem encontrada: ID={}, ativa={}", imagem.getId(), imagem.isAtiva());
            } catch (IllegalArgumentException e) {
                log.warn("Imagem não encontrada: {}", publicId);
                return ResponseEntity.notFound().build();
            }

            if (!imagem.isAtiva()) {
                log.warn("Imagem inativa: {}", publicId);
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            Pessoa solicitante = pessoaService.findByUsuarioId(userId);
            if (solicitante == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Pessoa donoImagem = imagem.getPessoa();
            if (donoImagem == null) {
                return ResponseEntity.notFound().build();
            }

            boolean isPropria = solicitante.getId().equals(donoImagem.getId());
            boolean hasMatch  = matchService.existeMatchAtivo(solicitante.getId(), donoImagem.getId());

            if (!isPropria && !hasMatch) {
                log.warn("[SECURITY] Acesso negado: {} -> {}", userId, publicId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            auditLogService.logImageAccess(userId, publicId, "PROXY_ACCESS");

            String presignedUrl = imagemService.gerarUrlPresignada(publicId);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, presignedUrl)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .build();

        } catch (Exception e) {
            log.error("Erro no proxy: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isPathTraversal(String path) {
        if (path == null) return true;
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("..") ||
                lowerPath.contains("./") ||
                lowerPath.contains("\\") ||
                lowerPath.contains("%2e") ||
                lowerPath.contains("/../") ||
                lowerPath.contains("\\..\\") ||
                lowerPath.contains("etc/passwd") ||
                lowerPath.contains("etc/shadow") ||
                lowerPath.contains("win.ini") ||
                lowerPath.contains("boot.ini") ||
                lowerPath.contains(".env") ||
                lowerPath.contains("web.xml") ||
                lowerPath.contains("application.properties") ||
                lowerPath.contains("passwd") ||
                lowerPath.contains("shadow");
    }

    private boolean isValidPublicId(String publicId) {
        if (publicId == null || publicId.trim().isEmpty()) return false;
        if (isPathTraversal(publicId)) return false;
        return PUBLIC_ID_PATTERN.matcher(publicId).matches() ||
                publicId.matches("^tinder_ufs/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+\\.[a-z]{2,5}$");
    }
}
