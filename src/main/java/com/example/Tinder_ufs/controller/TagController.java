package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.example.Tinder_ufs.service.TagService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("tags")
@AllArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Tags",
        description = "Endpoints para gerenciamento de tags"
)
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "Listar todas as tags")
    public ResponseEntity<List<Tag>> getAll(){
        return ResponseEntity.ok(tagService.getAll());
    }

    @GetMapping("/ativas")
    @Operation(summary = "Listar tags ativas")
    public ResponseEntity<List<Tag>> getAtivas(){
        return ResponseEntity.ok(tagService.getAtivas());
    }

    @PostMapping
    @Operation(summary = "Criar nova tag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Tag> create(@RequestBody @Valid Tag tag){
        return ResponseEntity.ok(tagService.create(tag));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar tag", description = "Bloqueado até controle de permissão")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<?> delete(@PathVariable String id){
        throw new RuntimeException("Endpoint desabilitado até controle de permissão (ADMIN)");
    }
}