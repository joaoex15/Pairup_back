package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.AuditLogService;
import com.example.Tinder_ufs.service.ImagemService;
import com.example.Tinder_ufs.service.MatchService;
import com.example.Tinder_ufs.service.PessoaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
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

    @Value("${RAILWAY_BUCKET_PUBLIC_URL}")
    private String bucketPublicUrl;

    private static final Set<String> MIME_ACEITOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9_/\\-]{3,150}\\.[a-z]{2,5}$"
    );

    @GetMapping("/**")
    public ResponseEntity<byte[]> proxyImagemHandler(HttpServletRequest request) {
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

        return proxyImagem(publicId, request);
    }

    private ResponseEntity<byte[]> proxyImagem(String publicId, HttpServletRequest request) {
        try {
            String userId = SecurityUtils.getUserIdOrThrow(request);

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

            String imageUrl = bucketPublicUrl + "/" + publicId;
            log.info("URL gerada: {}", imageUrl);

            // Garante que a URL pertence ao nosso bucket, evitando SSRF
            if (!imageUrl.startsWith(bucketPublicUrl)) {
                log.error("URL fora do domínio permitido: {}", imageUrl);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Falha ao baixar imagem: {}", imageUrl);
                return ResponseEntity.notFound().build();
            }

            String mimeType = imagem.getMimeType();
            if (mimeType == null || !MIME_ACEITOS.contains(mimeType)) {
                mimeType = "image/jpeg";
            }

            auditLogService.logImageAccess(userId, publicId, "PROXY_ACCESS");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setCacheControl("private, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Erro no proxy: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isPathTraversal(String path) {
        if (path == null) return true;
        // toLowerCase() unifica a detecção de variações de case (%2e cobre %2E também)
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

    private byte[] downloadImage(String imageUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "TinderUfs/1.0");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.error("Download falhou. HTTP Status: {}", responseCode);
            return null;
        }

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] result = outputStream.toByteArray();
            log.info("Imagem baixada: {} bytes", result.length);
            return result;
        }
    }
}
