package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.ImagemService;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@RestController
@RequestMapping("/imagens")
@AllArgsConstructor
@Tag(name = "Imagens", description = "Endpoints para upload e gerenciamento de fotos")
public class ImagemController {

    private static final Logger log = LoggerFactory.getLogger(ImagemController.class);

    /**
     * ✅ Whitelist de MIME types aceitos.
     *    O tipo é verificado pelo magic bytes (primeiros bytes do arquivo),
     *    não pelo Content-Type informado pelo cliente — que pode ser forjado.
     */
    private static final Set<String> MIME_ACEITOS = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** ✅ Limite de 5 MB por upload (também configurar no application.properties). */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    private final ImagemService imagemService;
    private final PessoaService pessoaService;

    /**
     * Faz upload de uma imagem para o perfil do usuário autenticado.
     *
     * ✅ Valida tamanho máximo (5 MB).
     * ✅ Valida MIME type pelos magic bytes — não confia no Content-Type do cliente.
     * ✅ Log de auditoria com pessoaId (sem nome do arquivo — dado sensível).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Fazer upload de uma nova imagem")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido, ausente ou tipo não permitido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado", content = @Content),
            @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho máximo de 5 MB", content = @Content)
    })
    public ResponseEntity<ImagemUploadResponseDTO> upload(
            @Parameter(description = "Arquivo de imagem (jpeg, png ou webp, máx 5 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Define se esta será a foto principal do perfil", required = true)
            @RequestParam("perfil") boolean perfil,
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        // ✅ Arquivo presente
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado.");
        }

        // ✅ Limite de tamanho
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo excede o tamanho máximo permitido de 5 MB.");
        }

        // ✅ Valida MIME pelos magic bytes — não pelo Content-Type do cliente
        String mimeType = detectMimeByMagicBytes(file);
        if (!MIME_ACEITOS.contains(mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de arquivo não permitido. Envie apenas JPEG, PNG ou WebP.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Perfil não encontrado. Complete seu cadastro primeiro.");
        }

        try {
            Imagem imagem = imagemService.salvarImagem(file, pessoa.getId(), perfil);

            log.info("[AUDIT] Imagem enviada por pessoaId={}, perfil={}", pessoa.getId(), perfil);

            ImagemUploadResponseDTO response = new ImagemUploadResponseDTO();
            response.setId(imagem.getId());
            response.setUrl(imagem.getUrl());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        // ✅ Outros erros inesperados sobem para o GlobalExceptionHandler (sem stack trace ao cliente)
    }

    /**
     * Deleta uma imagem do perfil do usuário autenticado.
     * ✅ Valida que a imagem pertence ao solicitante antes de deletar.
     */
    @DeleteMapping("/{imagemId}")
    @Operation(summary = "Deletar uma imagem")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Imagem deletada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão para deletar esta imagem", content = @Content),
            @ApiResponse(responseCode = "404", description = "Imagem não encontrada", content = @Content)
    })
    public ResponseEntity<Void> deletarImagem(
            @Parameter(description = "ID da imagem", required = true)
            @PathVariable String imagemId,
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado.");
        }

        try {
            imagemService.deletarImagem(imagemId, pessoa.getId());
            log.info("[AUDIT] Imagem deletada id={} por pessoaId={}", imagemId, pessoa.getId());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Detecta o MIME type pelos primeiros bytes do arquivo (magic bytes).
     *
     * Por que não usar file.getContentType()?
     *  O Content-Type é informado pelo cliente e pode ser facilmente forjado.
     *  Um atacante poderia enviar um script PHP com Content-Type "image/jpeg".
     *  Os magic bytes são a assinatura real do formato, definida dentro do arquivo.
     *
     * Assinaturas verificadas:
     *  - JPEG: FF D8 FF
     *  - PNG:  89 50 4E 47
     *  - WebP: 52 49 46 46 __ __ __ __ 57 45 42 50
     */
    private String detectMimeByMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) return "application/octet-stream";

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF &&
                    (header[1] & 0xFF) == 0xD8 &&
                    (header[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }

            // PNG: 89 50 4E 47
            if ((header[0] & 0xFF) == 0x89 &&
                    (header[1] & 0xFF) == 0x50 &&
                    (header[2] & 0xFF) == 0x4E &&
                    (header[3] & 0xFF) == 0x47) {
                return "image/png";
            }

            // WebP: RIFF....WEBP
            if (read >= 12 &&
                    header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                    header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "image/webp";
            }

            return "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}