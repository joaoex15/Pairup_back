package com.example.Tinder_ufs.controller;

import com.cloudinary.Cloudinary;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.ImagemService;
import com.example.Tinder_ufs.service.MatchService;
import com.example.Tinder_ufs.service.PessoaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

@RestController
@RequestMapping("/api/imagens/proxy")
public class ImagemProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImagemProxyController.class);

    private static final String CLOUDINARY_URL_PREFIX = "https://res.cloudinary.com/";
    private static final String PUBLIC_ID_REGEX = "^[a-zA-Z0-9][a-zA-Z0-9_/\\-]{8,98}[a-zA-Z0-9]$";
    private static final Set<String> MIME_ACEITOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Autowired private Cloudinary cloudinary;
    @Autowired private ImagemService imagemService;
    @Autowired private PessoaService pessoaService;
    @Autowired private MatchService matchService;

    @GetMapping("/{publicId}")
    public ResponseEntity<byte[]> proxyImagem(
            @PathVariable String publicId,
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        if (publicId == null || !publicId.matches(PUBLIC_ID_REGEX)) {
            return ResponseEntity.badRequest().build();
        }

        if (!temAcessoAImagem(userId, publicId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            String imageUrl = cloudinary.url()
                    .secure(true)
                    .generate(publicId);

            if (!imageUrl.startsWith(CLOUDINARY_URL_PREFIX)) {
                log.warn("[Security] URL gerada fora do domínio permitido para publicId hash={}",
                        publicId.hashCode());
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setCacheControl("private, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("[Proxy] Erro ao buscar imagem hash={}: {}", publicId.hashCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Verifica se o usuário tem acesso à imagem.
     * Acesso permitido se:
     *  1. O usuário é o dono da imagem, OU
     *  2. O usuário tem um match ativo com o dono da imagem.
     */
    private boolean temAcessoAImagem(String userId, String publicId) {
        Imagem imagem = imagemService.findByPublicId(publicId);
        if (imagem == null) return false;

        Pessoa solicitante = pessoaService.findByUsuarioId(userId);
        if (solicitante == null) return false;

        // ✅ CORREÇÃO AQUI: usa getPessoa().getId() em vez de getPessoaId()
        Pessoa donoImagem = imagem.getPessoa();
        if (donoImagem == null) return false;

        String donoImagemId = donoImagem.getId();

        // Caso 1: o solicitante é o dono
        if (solicitante.getId().equals(donoImagemId)) return true;

        // Caso 2: existe match ativo entre o solicitante e o dono da imagem
        return matchService.existeMatchAtivo(solicitante.getId(), donoImagemId);
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
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}