package com.example.pairup.imagem;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.shared.security.SecurityUtils;
import com.example.pairup.pessoa.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/imagens")
@RequiredArgsConstructor
@Tag(name = "Imagens", description = "Upload e gerenciamento das fotos do perfil")
public class ImagemController {

    private final ImagemService imagemService;
    private final PessoaService pessoaService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Enviar foto", description = "JPEG, PNG ou WebP, até 5 MB. perfil=true define a foto principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto enviada"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou perfil inexistente"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "413", description = "Arquivo acima do limite")
    })
    public ResponseEntity<ImagemUploadResponseDTO> uploadImagem(
            @Valid @ModelAttribute ImagemUploadDTO uploadDTO) {

        try {
            String userId = SecurityUtils.getUserIdOrThrow();

            Pessoa pessoa = pessoaService.findByUsuarioId(userId);
            if (pessoa == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ImagemUploadResponseDTO.error("Perfil não encontrado. Complete seu cadastro primeiro."));
            }

            String pessoaId = pessoa.getId();

            Imagem imagem = imagemService.salvarImagem(
                    uploadDTO.getFile(),
                    pessoaId,
                    uploadDTO.isPerfil()
            );

            ImagemUploadResponseDTO response = ImagemUploadResponseDTO.fromImagem(
                    imagem,
                    uploadDTO.getFile().getOriginalFilename()
            );
            response.setUrl("/api/imagens/proxy/" + imagem.getPublicId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (SecurityException e) {
            log.error("Erro de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro interno: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ImagemUploadResponseDTO.error("Erro ao processar upload: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{imagemId}")
    @Operation(summary = "Remover foto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto removida"),
            @ApiResponse(responseCode = "400", description = "Última foto do perfil ou perfil inexistente"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "A foto é de outra pessoa"),
            @ApiResponse(responseCode = "404", description = "Foto não encontrada")
    })
    public ResponseEntity<ImagemUploadResponseDTO> deletarImagem(
            @PathVariable String imagemId) {

        try {
            String userId = SecurityUtils.getUserIdOrThrow();

            Pessoa pessoa = pessoaService.findByUsuarioId(userId);
            if (pessoa == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ImagemUploadResponseDTO.error("Perfil não encontrado"));
            }

            String pessoaId = pessoa.getId();

            imagemService.deletarImagem(imagemId, pessoaId);

            return ResponseEntity.ok(ImagemUploadResponseDTO.success("Imagem deletada com sucesso"));

        } catch (IllegalArgumentException e) {
            log.warn("Imagem não encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (SecurityException e) {
            log.error("Erro de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Erro de estado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ImagemUploadResponseDTO.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro interno: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ImagemUploadResponseDTO.error("Erro ao deletar imagem: " + e.getMessage()));
        }
    }

    @GetMapping("/minhas-imagens")
    @Operation(summary = "Listar minhas fotos ativas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fotos retornadas"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<ImagemUploadResponseDTO>> listarMinhasImagens() {
        try {
            String userId = SecurityUtils.getUserIdOrThrow();

            Pessoa pessoa = pessoaService.findByUsuarioId(userId);
            if (pessoa == null) {
                return ResponseEntity.ok(List.of());
            }

            String pessoaId = pessoa.getId();

            List<ImagemUploadResponseDTO> imagens = imagemService
                    .listarImagensAtivasPorUsuario(pessoaId)
                    .stream()
                    .map(img -> {
                        ImagemUploadResponseDTO dto = new ImagemUploadResponseDTO();
                        dto.setId(img.getId());
                        dto.setUrl("/api/imagens/proxy/" + img.getPublicId());
                        dto.setPerfil(img.isPerfil());
                        dto.setTamanhoBytes(img.getTamanhoBytes());
                        dto.setSucesso(true);
                        return dto;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(imagens);

        } catch (SecurityException e) {
            log.error("Erro de segurança: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Erro ao listar imagens: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}