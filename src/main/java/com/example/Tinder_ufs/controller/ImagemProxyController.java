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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
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
    private final S3Presigner s3Presigner;

    @Value("${RAILWAY_BUCKET_NAME}")
    private String bucketName;

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

            byte[] imageBytes = downloadImageFromBucket(publicId);
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Falha ao baixar imagem do bucket: {}", publicId);
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

    private byte[] downloadImageFromBucket(String publicId) throws Exception {
        GetObjectRequest getObj = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(publicId)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(getObj)
                        .build()
        );

        String presignedUrl = presigned.url().toString();
        log.debug("Presigned URL gerada internamente para: {}", publicId);

        HttpURLConnection connection = (HttpURLConnection) new URL(presignedUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);

        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            log.error("Download do bucket falhou. HTTP {}: {}", status, publicId);
            return null;
        }

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            byte[] result = out.toByteArray();
            log.info("Imagem baixada do bucket: {} bytes ({})", result.length, publicId);
            return result;
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
