package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.service.MatchService;
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
@RequestMapping("matches")
@AllArgsConstructor
@Tag(name = "Matches", description = "Endpoints para gerenciamento de matches entre pessoas")
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/{pessoaId}")
    @Operation(summary = "Listar matches de uma pessoa", description = "Retorna todos os matches de uma pessoa")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de matches retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<List<Match>> listarMeusMatches(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String pessoaId){
        return ResponseEntity.ok(matchService.listarMeusMatches(pessoaId));
    }

    @PutMapping("/desfazer/{id}")
    @Operation(summary = "Desfazer um match", description = "Remove um match existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Match desfeito com sucesso"),
            @ApiResponse(responseCode = "404", description = "Match não encontrado")
    })
    public ResponseEntity<Void> desfazerMatch(
            @Parameter(description = "ID do match", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        matchService.desfazerMatch(id);
        return ResponseEntity.ok().build();
    }
}