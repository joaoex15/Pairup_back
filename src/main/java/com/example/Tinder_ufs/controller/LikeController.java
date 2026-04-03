package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("likes")
@AllArgsConstructor
@Tag(name = "Likes", description = "Endpoints para gerenciamento de likes")
public class LikeController {

    private static final Logger log = LoggerFactory.getLogger(LikeController.class);

    private final LikeService likeService;

    /**
     * Registra um like do usuário autenticado em outra pessoa.
     * ✅ origemId vem do JWT — previne IDOR (dar like fingindo ser outro usuário).
     * ✅ Impede auto-like.
     * ✅ Log de auditoria registra a ação (sem IDs de destino em nível INFO para privacidade).
     */
    @PostMapping
    @Operation(summary = "Dar like")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Like registrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou auto-like"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> darLike(
            @Parameter(description = "ID da pessoa que recebe o like")
            @RequestParam @NotBlank String destinoId,
            HttpServletRequest request) {

        String origemId = SecurityUtils.getUserIdOrThrow(request);

        // ✅ Impede auto-like
        if (origemId.equals(destinoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Você não pode dar like em si mesmo.");
        }

        Object resultado = likeService.darLike(origemId, destinoId);
        log.info("[AUDIT] Like registrado de pessoaId={}", origemId);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Lista os likes que o usuário autenticado deu.
     * ✅ pessoaId vem do JWT — usuário só pode ver seus próprios likes.
     * ✅ Paginado — evita OOM com muitos likes.
     */
    @GetMapping("/dados")
    @Operation(summary = "Listar likes dados pelo usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Likes retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Page<Like>> listarLikesDados(
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {

        String pessoaId = SecurityUtils.getUserIdOrThrow(request);
        return ResponseEntity.ok(likeService.listarLikesDados(pessoaId, pageable));
    }

    /**
     * Lista os likes que o usuário autenticado recebeu.
     * ✅ pessoaId vem do JWT — usuário só pode ver seus próprios likes recebidos.
     * ✅ Paginado.
     */
    @GetMapping("/recebidos")
    @Operation(summary = "Listar likes recebidos pelo usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Likes retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Page<Like>> listarLikesRecebidos(
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {

        String pessoaId = SecurityUtils.getUserIdOrThrow(request);
        return ResponseEntity.ok(likeService.listarLikesRecebidos(pessoaId, pageable));
    }
}