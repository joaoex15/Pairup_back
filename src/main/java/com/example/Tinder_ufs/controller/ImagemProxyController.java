package com.example.Tinder_ufs.controller;

import com.cloudinary.Cloudinary;
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
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/imagens/proxy")
@RequiredArgsConstructor
public class ImagemProxyController {

    private final Cloudinary cloudinary;
    private final ImagemService imagemService;
    private final PessoaService pessoaService;
    private final MatchService matchService;
    private final AuditLogService auditLogService;

    private static final String CLOUDINARY_URL_PREFIX = "https://res.cloudinary.com/";
    private static final Set<String> MIME_ACEITOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /**
     * Valida se o publicId é seguro (previne path traversal)
     */
    private boolean isValidPublicId(String publicId) {
        if (publicId == null || publicId.trim().isEmpty()) {
            return false;
        }

        // Bloquear qualquer tentativa de path traversal
        String lowerId = publicId.toLowerCase();
        if (lowerId.contains("..") ||
                lowerId.contains("./") ||
                lowerId.contains("\\") ||
                lowerId.contains("%2e") ||
                lowerId.contains("%2E") ||
                lowerId.contains("/../") ||
                lowerId.contains("\\..\\") ||
                lowerId.contains("etc") ||
                lowerId.contains("passwd") ||
                lowerId.contains("shadow") ||
                lowerId.contains("hosts") ||
                lowerId.contains(".env") ||
                lowerId.contains("web.xml")) {
            return false;
        }

        // Formato válido: letras, números, underline, hífen, barra
        return publicId.matches("^[a-zA-Z0-9][a-zA-Z0-9_/\\-]*$");
    }

    @GetMapping("/{publicId:.*}")
    public ResponseEntity<byte[]> proxyImagem(
            @PathVariable String publicId,
            HttpServletRequest request) {

        try {
            String userId = SecurityUtils.getUserIdOrThrow(request);

            log.info("Proxy request - publicId: {}, userId: {}", publicId, userId);

            // ✅ CORREÇÃO 1: Validar path traversal
            if (!isValidPublicId(publicId)) {
                log.warn("[SECURITY] Path traversal bloqueado: {} from user: {}", publicId, userId);
                auditLogService.logSecurityViolation(userId, "Path traversal blocked: " + publicId);
                return ResponseEntity.badRequest().build();  // 400 Bad Request
            }

            // ✅ CORREÇÃO 2: Buscar imagem pelo publicId
            Imagem imagem;
            try {
                imagem = imagemService.findByPublicId(publicId);
                log.info("Imagem encontrada: ID={}, publicId={}, ativa={}",
                        imagem.getId(), imagem.getPublicId(), imagem.isAtiva());
            } catch (IllegalArgumentException e) {
                log.warn("Imagem não encontrada: {} from user: {}", publicId, userId);
                return ResponseEntity.notFound().build();  // 404 Not Found
            }

            if (!imagem.isAtiva()) {
                log.warn("Imagem inativa: {}", publicId);
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            // ✅ CORREÇÃO 3: Verificar acesso
            Pessoa solicitante = pessoaService.findByUsuarioId(userId);
            if (solicitante == null) {
                log.warn("Solicitante não encontrado para userId: {}", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Pessoa donoImagem = imagem.getPessoa();
            if (donoImagem == null) {
                log.warn("Dono da imagem não encontrado para publicId: {}", publicId);
                return ResponseEntity.notFound().build();
            }

            boolean isPropria = solicitante.getId().equals(donoImagem.getId());
            boolean hasMatch = matchService.existeMatchAtivo(solicitante.getId(), donoImagem.getId());

            log.info("Acesso - Propria: {}, HasMatch: {}", isPropria, hasMatch);

            if (!isPropria && !hasMatch) {
                log.warn("[SECURITY] Acesso negado: {} -> {}", userId, publicId);
                auditLogService.logSecurityViolation(userId, "Acesso negado à imagem: " + publicId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // ✅ CORREÇÃO 4: Gerar URL do Cloudinary
            String imageUrl;
            try {
                imageUrl = cloudinary.url()
                        .secure(true)
                        .generate(publicId);
                log.info("URL gerada: {}", imageUrl);
            } catch (Exception e) {
                log.error("Erro ao gerar URL do Cloudinary: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            if (!imageUrl.startsWith(CLOUDINARY_URL_PREFIX)) {
                log.error("URL fora do domínio permitido: {}", imageUrl);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            // ✅ CORREÇÃO 5: Baixar imagem
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Falha ao baixar imagem: {}", imageUrl);
                return ResponseEntity.notFound().build();
            }

            // ✅ CORREÇÃO 6: Obter MIME type
            String mimeType = imagem.getMimeType();
            if (mimeType == null || !MIME_ACEITOS.contains(mimeType)) {
                mimeType = "image/png"; // fallback
            }

            // Registrar acesso
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

        String contentType = connection.getContentType();
        log.info("Content-Type da imagem: {}", contentType);

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