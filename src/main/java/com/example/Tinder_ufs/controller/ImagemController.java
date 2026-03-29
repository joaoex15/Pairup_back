package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.service.ImagemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/imagens")
@Tag(name = "Imagens", description = "Endpoints para upload e gerenciamento de imagens")
public class ImagemController {

    @Autowired
    private ImagemService imagemService;

    // ✅ Tipos MIME aceitos
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    // ✅ Tamanho máximo: 10 MB
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L;

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de uma única imagem")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upload realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou não enviado"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "415", description = "Formato de imagem não suportado")
    })
    public ResponseEntity<ImagemUploadResponseDTO> uploadImagem(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("pessoaId") String pessoaId,
            @RequestParam(value = "perfil", defaultValue = "false") boolean perfil,
            HttpServletRequest request) {

        // ✅ pessoaId deve ser o próprio usuário autenticado — previne IDOR
        String authenticatedUserId = (String) request.getAttribute("userId");
        if (!pessoaId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você só pode fazer upload de imagens para seu próprio perfil.");
        }

        validarArquivo(arquivo);

        return ResponseEntity.ok(imagemService.uploadImagem(arquivo, pessoaId, perfil));
    }

    @PostMapping(value = "/upload/multiplas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de múltiplas imagens")
    public ResponseEntity<List<ImagemUploadResponseDTO>> uploadMultiplasImagens(
            @RequestParam("arquivos") List<MultipartFile> arquivos,
            @RequestParam("pessoaId") String pessoaId,
            HttpServletRequest request) {

        // ✅ Verifica que o usuário autenticado só sobe para si mesmo
        String authenticatedUserId = (String) request.getAttribute("userId");
        if (!pessoaId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você só pode fazer upload de imagens para seu próprio perfil.");
        }

        arquivos.forEach(this::validarArquivo);

        return ResponseEntity.ok(imagemService.uploadMultiplasImagens(arquivos, pessoaId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Buscar imagem por ID")
    public ResponseEntity<Imagem> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(imagemService.buscarPorId(id));
    }

    @GetMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Listar imagens por pessoa")
    public ResponseEntity<List<Imagem>> buscarPorPessoa(@PathVariable String pessoaId) {
        return ResponseEntity.ok(imagemService.listarPorPessoa(pessoaId));
    }

    @GetMapping("/pessoa/{pessoaId}/perfil")
    @Operation(summary = "Buscar imagem de perfil")
    public ResponseEntity<Imagem> buscarImagemPerfil(@PathVariable String pessoaId) {
        return ResponseEntity.ok(imagemService.buscarImagemPerfil(pessoaId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEFINIR PERFIL
    // ─────────────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/perfil")
    @Operation(
            summary = "Definir imagem como perfil",
            description = "Define a imagem informada como foto de perfil da pessoa, " +
                    "removendo o status de perfil da anterior."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Imagem de perfil atualizada"),
            @ApiResponse(responseCode = "404", description = "Imagem não encontrada")
    })
    public ResponseEntity<Imagem> definirComoPerfil(
            @Parameter(description = "ID da imagem", required = true)
            @PathVariable String id) {
        return ResponseEntity.ok(imagemService.definirComoPerfil(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELEÇÃO
    // ─────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar imagem", description = "Remove do Drive e do MongoDB")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Imagem deletada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Imagem não encontrada")
    })
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        imagemService.deletarImagem(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Deletar todas as imagens de uma pessoa")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Imagens deletadas com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Void> deletarTodasPorPessoa(
            @PathVariable String pessoaId,
            HttpServletRequest request) {

        // ✅ Só o próprio usuário pode deletar suas imagens
        String authenticatedUserId = (String) request.getAttribute("userId");
        if (!pessoaId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você só pode deletar suas próprias imagens.");
        }

        imagemService.deletarTodasPorPessoa(pessoaId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio ou não enviado.");
        }

        String contentType = arquivo.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato não suportado. Use: JPEG, PNG ou WebP.");
        }

        if (arquivo.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo muito grande. Tamanho máximo: 10 MB.");
        }
    }
}