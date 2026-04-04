package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.ImagemUploadDTO;
import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.ImagemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/imagens")
@RequiredArgsConstructor
public class ImagemController {

    private final ImagemService imagemService;

    @PostMapping("/upload")
    public ResponseEntity<ImagemUploadResponseDTO> uploadImagem(
            @Valid @ModelAttribute ImagemUploadDTO uploadDTO,
            HttpServletRequest request) {

        try {
            String userId = SecurityUtils.getUserIdOrThrow(request);

            Imagem imagem = imagemService.salvarImagem(
                    uploadDTO.getFile(),
                    userId,
                    uploadDTO.isPerfil()
            );

            ImagemUploadResponseDTO response = ImagemUploadResponseDTO.fromImagem(
                    imagem,
                    uploadDTO.getFile().getOriginalFilename()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (SecurityException e) {
            log.error("Erro de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro interno: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ImagemUploadResponseDTO.error("Erro ao processar upload"));
        }
    }

    @DeleteMapping("/{imagemId}")
    public ResponseEntity<ImagemUploadResponseDTO> deletarImagem(
            @PathVariable String imagemId,
            HttpServletRequest request) {

        try {
            String userId = SecurityUtils.getUserIdOrThrow(request);
            imagemService.deletarImagem(imagemId, userId);

            ImagemUploadResponseDTO response = new ImagemUploadResponseDTO();
            response.setSucesso(true);
            response.setMensagem("Imagem deletada com sucesso");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        }
    }

    @GetMapping("/minhas-imagens")
    public ResponseEntity<List<ImagemUploadResponseDTO>> listarMinhasImagens(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        List<ImagemUploadResponseDTO> imagens = imagemService
                .listarImagensAtivasPorUsuario(userId)
                .stream()
                .map(img -> {
                    ImagemUploadResponseDTO dto = new ImagemUploadResponseDTO();
                    dto.setId(img.getId());
                    dto.setUrl(img.getUrl());
                    dto.setPerfil(img.isPerfil());
                    dto.setTamanhoBytes(img.getTamanhoBytes());
                    dto.setSucesso(true);
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(imagens);
    }
}