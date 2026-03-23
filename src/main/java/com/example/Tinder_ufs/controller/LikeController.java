package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("likes")
@AllArgsConstructor
@Tag(name = "Likes", description = "Endpoints para gerenciamento de likes entre pessoas")
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    @Operation(summary = "Dar um like", description = "Registra um like de uma pessoa para outra")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Like registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Void> darLike(
            @Parameter(description = "ID da pessoa que está dando o like", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @RequestParam String origemId,

            @Parameter(description = "ID da pessoa que está recebendo o like", required = true, example = "123e4567-e89b-12d3-a456-426614174001")
            @RequestParam String destinoId
    ){
        likeService.darLike(origemId, destinoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dados/{pessoaId}")
    @Operation(summary = "Listar likes dados", description = "Retorna todos os likes que uma pessoa deu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de likes retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<List<Like>> listarLikesDados(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String pessoaId) {
        List<Like> likes = likeService.listarLikesDados(pessoaId);
        return ResponseEntity.ok(likes);
    }

    @GetMapping("/recebidos/{pessoaId}")
    @Operation(summary = "Listar likes recebidos", description = "Retorna todos os likes que uma pessoa recebeu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de likes retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<List<Like>> listarLikesRecebidos(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String pessoaId) {
        List<Like> likes = likeService.listarLikesRecebidos(pessoaId);
        return ResponseEntity.ok(likes);
    }
}