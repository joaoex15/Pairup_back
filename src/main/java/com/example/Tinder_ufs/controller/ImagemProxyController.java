package com.example.Tinder_ufs.controller;

import com.google.api.services.drive.Drive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/imagens/proxy")
public class ImagemProxyController {

    @Autowired
    private Drive googleDriveService;

    /**
     * Serve a imagem do Google Drive como bytes diretamente.
     * O frontend usa: /api/imagens/proxy/<fileId>
     * Sem cookies, sem CORS, sem bloqueio de terceiros.
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> proxyImagem(@PathVariable String fileId) {
        try {
            // Busca metadados para obter o mimeType
            com.google.api.services.drive.model.File meta = googleDriveService.files()
                    .get(fileId)
                    .setFields("mimeType, name")
                    .execute();

            // Baixa o conteúdo do arquivo
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            googleDriveService.files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(outputStream);

            byte[] imageBytes = outputStream.toByteArray();

            if (imageBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            // Define o Content-Type correto
            String mimeType = meta.getMimeType() != null ? meta.getMimeType() : "image/jpeg";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mimeType));
            // Cache por 1 hora no browser
            headers.setCacheControl("public, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("[Proxy] Erro ao buscar imagem " + fileId + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}