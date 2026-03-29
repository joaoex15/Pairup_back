package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.service.LikeService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("likes")
@AllArgsConstructor
@Tag(name = "Likes", description = "Endpoints para gerenciamento de likes")
public class LikeController {

    private final LikeService likeService;

    /**
     * Registra um like do usuário autenticado em outra pessoa.
     * ✅ origemId vem do JWT — o cliente nunca pode informar quem está dando o like.
     *    Isso previne IDOR (dar like fingindo ser outro usuário).
     */
    @PostMapping
    @Operation(summary = "Dar like")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Like registrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> darLike(
            @Parameter(description = "ID da pessoa que recebe o like")
            @RequestParam @NotBlank String destinoId,
            HttpServletRequest request) {

        // ✅ origemId extraído do JWT — nunca confiamos no cliente para isso
        String origemId = (String) request.getAttribute("userId");
        if (origemId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        if (origemId.equals(destinoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Você não pode dar like em si mesmo.");
        }

        return ResponseEntity.ok(likeService.darLike(origemId, destinoId));
    }

    /**
     * Lista os likes que o usuário autenticado deu.
     * ✅ pessoaId vem do JWT — usuário só pode ver seus próprios likes.
     */
    @GetMapping("/dados")
    @Operation(summary = "Listar likes dados pelo usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Likes retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<Like>> listarLikesDados(HttpServletRequest request) {

        String pessoaId = (String) request.getAttribute("userId");
        if (pessoaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        return ResponseEntity.ok(likeService.listarLikesDados(pessoaId));
    }

    /**
     * Lista os likes que o usuário autenticado recebeu.
     */
    @GetMapping("/recebidos")
    @Operation(summary = "Listar likes recebidos pelo usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Likes retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<Like>> listarLikesRecebidos(HttpServletRequest request) {

        String pessoaId = (String) request.getAttribute("userId");
        if (pessoaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        return ResponseEntity.ok(likeService.listarLikesRecebidos(pessoaId));
    }
}