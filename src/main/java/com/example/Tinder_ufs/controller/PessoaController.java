package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.service.PessoaService;
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
@RequestMapping("pessoas")
@AllArgsConstructor
@Tag(name = "Pessoas", description = "Endpoints para gerenciamento de pessoas")
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    @Operation(summary = "Listar todas as pessoas", description = "Retorna uma lista com todas as pessoas cadastradas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    })
    public ResponseEntity<List<Pessoa>> getAll(){
        return ResponseEntity.ok(pessoaService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pessoa por ID", description = "Retorna os detalhes de uma pessoa específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Pessoa> getById(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        return ResponseEntity.ok(pessoaService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Criar nova pessoa", description = "Cadastra uma nova pessoa no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Pessoa> create(
            @Parameter(description = "Dados da pessoa", required = true)
            @RequestBody Pessoa pessoa){
        return ResponseEntity.ok(pessoaService.create(pessoa));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pessoa", description = "Atualiza os dados de uma pessoa existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Pessoa> update(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id,

            @Parameter(description = "Dados atualizados da pessoa", required = true)
            @RequestBody Pessoa pessoa){
        pessoa.setId(id);
        return ResponseEntity.ok(pessoaService.update(pessoa));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar pessoa", description = "Remove uma pessoa do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Pessoa deletada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da pessoa", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        pessoaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}