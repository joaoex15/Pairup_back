package com.example.pairup.match;

import com.example.pairup.pessoa.PessoaService;
import com.example.pairup.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Passar" no deck. Fica salvo para a pessoa não voltar a aparecer quando a página recarrega.
 * Não é visível para quem foi dispensado.
 */
@RestController
@RequestMapping("dislikes")
@AllArgsConstructor
@Tag(name = "Dislikes", description = "Perfis dispensados no deck")
public class DislikeController {

    private final DislikeService dislikeService;
    private final PessoaService pessoaService;

    @PostMapping
    @Operation(summary = "Passar em um perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Registrado"),
            @ApiResponse(responseCode = "400", description = "Destino ausente, inexistente ou a própria pessoa"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Cadastro incompleto")
    })
    public ResponseEntity<Void> darDislike(
            @Parameter(description = "ID da pessoa dispensada")
            @RequestParam String destinoId) {

        String userId = SecurityUtils.getUserIdOrThrow();
        String origemId = pessoaService.getPessoaIdComCadastroCompletoOrThrow(userId);

        if (destinoId == null || destinoId.isBlank() || origemId.equals(destinoId)
                || pessoaService.findById(destinoId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido");
        }

        dislikeService.darDislike(origemId, destinoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dados")
    @Operation(summary = "Listar perfis dispensados pelo usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dislikes retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Page<Dislike>> listarDislikesDados(
            @PageableDefault(size = 20) Pageable pageable) {

        String userId = SecurityUtils.getUserIdOrThrow();
        String pessoaId = pessoaService.getPessoaIdOrThrow(userId);
        return ResponseEntity.ok(dislikeService.listarDislikesDados(pessoaId, pageable));
    }
}
