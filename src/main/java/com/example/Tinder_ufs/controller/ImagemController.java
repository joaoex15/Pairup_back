package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/imagens")
@AllArgsConstructor
@Tag(name = "Imagens", description = "Endpoints para upload e gerenciamento de fotos")
public class ImagemController {

    private final ImagemService imagemService;
    private final PessoaService pessoaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Fazer upload de uma nova imagem")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou ausente", content = @Content),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado", content = @Content)
    })
    public ResponseEntity<ImagemUploadResponseDTO> upload(
            @Parameter(description = "Arquivo de imagem a ser enviado", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Define se esta será a foto principal do perfil", required = true)
            @RequestParam("perfil") boolean perfil,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado. Complete seu cadastro primeiro.");
        }

        try {
            Imagem imagem = imagemService.salvarImagem(file, pessoa.getId(), perfil);

            ImagemUploadResponseDTO response = new ImagemUploadResponseDTO();
            response.setId(imagem.getId());
            response.setUrl(imagem.getUrl());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/{imagemId}")
    @Operation(summary = "Deletar uma imagem")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Imagem deletada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão para deletar esta imagem", content = @Content),
            @ApiResponse(responseCode = "404", description = "Imagem não encontrada", content = @Content)
    })
    public ResponseEntity<Void> deletarImagem(
            @Parameter(description = "ID da imagem gerado no MongoDB", required = true)
            @PathVariable String imagemId,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado.");
        }

        try {
            imagemService.deletarImagem(imagemId, pessoa.getId());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}