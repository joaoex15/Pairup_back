package com.example.Tinder_ufs.controller;

import com.google.api.services.drive.Drive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

/**
 * Proxy de imagens do Google Drive.
 *
 * ✅ Este endpoint está protegido pelo SecurityConfig (.anyRequest().authenticated()).
 *    Apenas usuários com JWT válido conseguem acessar.
 *    Não é mais necessário listar aqui como permitAll().
 */
@RestController
@RequestMapping("/api/imagens/proxy")
public class ImagemProxyController {

    @Autowired
    private Drive googleDriveService;

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> proxyImagem(@PathVariable String fileId) {

        // ✅ Valida que o fileId tem formato aceitável (apenas alfanumérico + hífen/underscore)
        // Previne path traversal ou injeção de caracteres maliciosos
        if (fileId == null || !fileId.matches("^[a-zA-Z0-9_\\-]{10,100}$")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            com.google.api.services.drive.model.File meta = googleDriveService.files()
                    .get(fileId)
                    .setFields("mimeType, name")
                    .execute();

            // ✅ Garante que só serve tipos de imagem — nunca um PDF, script etc.
            String mimeType = meta.getMimeType() != null ? meta.getMimeType() : "image/jpeg";
            if (!mimeType.startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            googleDriveService.files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(outputStream);

            byte[] imageBytes = outputStream.toByteArray();

            if (imageBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            // Cache público por 1 hora no browser
            headers.setCacheControl("public, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("[Proxy] Erro ao buscar imagem " + fileId + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}