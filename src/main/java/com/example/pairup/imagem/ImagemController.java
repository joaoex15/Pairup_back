package com.example.pairup.imagem;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.shared.security.SecurityUtils;
import com.example.pairup.pessoa.PessoaService;
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
public class ImagemController {

    private final ImagemService imagemService;
    private final PessoaService pessoaService;

    @PostMapping("/upload")
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