package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.example.Tinder_ufs.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
// NÃO importar io.swagger.v3.oas.annotations.tags.Tag
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("tags")
@AllArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags", description = "Endpoints para gerenciamento de tags")  // Nome totalmente qualificado
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "Listar todas as tags", description = "Retorna uma lista com todas as tags cadastradas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de tags retornada com sucesso")
    })
    public ResponseEntity<List<Tag>> getAll(){
        return ResponseEntity.ok(tagService.getAll());
    }

    @GetMapping("/ativas")
    @Operation(summary = "Listar tags ativas", description = "Retorna uma lista com todas as tags ativas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de tags ativas retornada com sucesso")
    })
    public ResponseEntity<List<Tag>> getAtivas(){
        return ResponseEntity.ok(tagService.getAtivas());
    }

    @PostMapping
    @Operation(summary = "Criar nova tag", description = "Cadastra uma nova tag no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tag criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Tag> create(
            @Parameter(description = "Dados da tag", required = true)
            @RequestBody Tag tag){
        return ResponseEntity.ok(tagService.create(tag));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar tag", description = "Remove uma tag do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tag deletada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tag não encontrada")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da tag", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}