package com.example.Tinder_ufs.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
import java.util.Map;

@RestController
@RequestMapping("/api/imagens/proxy")
public class ImagemProxyController {

    @Autowired
    private Cloudinary cloudinary;

    @GetMapping("/{publicId}")
    public ResponseEntity<byte[]> proxyImagem(@PathVariable String publicId) {

        // Valida que o publicId tem formato aceitável
        if (publicId == null || !publicId.matches("^[a-zA-Z0-9_\\-]{10,100}$")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Gerar URL segura da imagem no Cloudinary
            String imageUrl = cloudinary.url()
                    .secure(true)
                    .generate(publicId);

            // Baixar a imagem da URL
            byte[] imageBytes = downloadImage(imageUrl);

            if (imageBytes == null || imageBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            // Detectar mime type pela extensão ou usar padrão
            String mimeType = detectMimeType(publicId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setCacheControl("public, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("[Proxy] Erro ao buscar imagem " + publicId + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
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
        if (publicId.endsWith(".png")) {
            return "image/png";
        } else if (publicId.endsWith(".gif")) {
            return "image/gif";
        } else if (publicId.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "image/jpeg"; // padrão
        }
    }
}