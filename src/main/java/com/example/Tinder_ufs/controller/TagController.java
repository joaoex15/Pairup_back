package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /**
     * Listagem de todas as tags.
     * Público — tags são metadados de filtro visíveis na UI de cadastro.
     */
    @GetMapping
    @Operation(summary = "Listar todas as tags")
    public ResponseEntity<List<Tag>> getAll() {
        return ResponseEntity.ok(tagService.getAll());
    }

    /**
     * Listagem de tags ativas.
     * Público — usado na seleção de interesses no cadastro.
     */
    @GetMapping("/ativas")
    @Operation(summary = "Listar tags ativas")
    public ResponseEntity<List<Tag>> getAtivas() {
        return ResponseEntity.ok(tagService.getAtivas());
    }

    /**
     * Cria uma nova tag.
     * ✅ Exige JWT — impede que bots criem tags em massa e poluam o banco.
     *
     * TODO: quando controle de roles estiver implementado, restringir a ADMIN.
     */
    @PostMapping
    @Operation(summary = "Criar nova tag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Tag> create(
            @RequestBody @Valid Tag tag,
            HttpServletRequest request) {

        // ✅ Exige autenticação — qualquer usuário autenticado pode criar tags por ora
        SecurityUtils.getUserIdOrThrow(request);

        return ResponseEntity.ok(tagService.create(tag));
    }

    /**
     * Exclusão de tag.
     * ✅ Retorna 403 explícito em vez de RuntimeException.
     *    RuntimeException não tratada pode vazar stack trace ao cliente.
     *
     * TODO: quando controle de roles estiver implementado, liberar apenas para ADMIN.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar tag", description = "Reservado para ADMIN. Temporariamente bloqueado.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        // ✅ 403 explícito — nunca RuntimeException que poderia expor detalhes internos
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}