package com.example.Tinder_ufs.controller;

import com.cloudinary.Cloudinary;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.ImagemService;
import com.example.Tinder_ufs.service.MatchService;
import com.example.Tinder_ufs.service.PessoaService;
import com.example.Tinder_ufs.service.AuditLogService;
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
    private static final String PUBLIC_ID_REGEX = "^[a-zA-Z0-9][a-zA-Z0-9_/\\-]{8,98}[a-zA-Z0-9]$";
    private static final Set<String> MIME_ACEITOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @GetMapping("/{publicId}")
    public ResponseEntity<byte[]> proxyImagem(
            @PathVariable String publicId,
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        // Validação do publicId
        if (publicId == null || !publicId.matches(PUBLIC_ID_REGEX)) {
            auditLogService.logSecurityViolation(userId, "Tentativa de acesso com publicId inválido: " + publicId);
            return ResponseEntity.badRequest().build();
        }

        // Verificação de acesso
        if (!temAcessoAImagem(userId, publicId)) {
            auditLogService.logSecurityViolation(userId, "Tentativa de acesso não autorizado à imagem: " + publicId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            String imageUrl = cloudinary.url()
                    .secure(true)
                    .generate(publicId);

            if (!imageUrl.startsWith(CLOUDINARY_URL_PREFIX)) {
                log.warn("[Security] URL gerada fora do domínio permitido para publicId: {}", publicId);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            String mimeType = detectMimeType(publicId);
            if (!MIME_ACEITOS.contains(mimeType)) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            // Log de acesso bem-sucedido
            auditLogService.logImageAccess(userId, publicId, "PROXY_ACCESS");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setCacheControl("private, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("[Proxy] Erro ao buscar imagem {}: {}", publicId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private boolean temAcessoAImagem(String userId, String publicId) {
        Imagem imagem = imagemService.findByPublicId(publicId);
        if (imagem == null || !imagem.isAtiva()) return false;

        var solicitante = pessoaService.findByUsuarioId(userId);
        if (solicitante == null) return false;

        var donoImagem = imagem.getPessoa();
        if (donoImagem == null) return false;

        // Dono da imagem tem acesso
        if (solicitante.getId().equals(donoImagem.getId())) return true;

        // Verificar match ativo
        return matchService.existeMatchAtivo(solicitante.getId(), donoImagem.getId());
    }

    private byte[] downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private String detectMimeType(String publicId) {
        String lower = publicId.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}