package com.example.pairup.tag;

import com.example.pairup.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tags")  // ✅ CORRIGIDO: Adicionar barra no início
@AllArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Tags",
        description = "Endpoints para gerenciamento de tags"
)
public class TagController {

    private final TagService tagService;

    /**
     * Listagem de todas as tags.
     * ✅ Público - qualquer um pode ver
     */
    @GetMapping
    @Operation(summary = "Listar todas as tags")
    @SecurityRequirements
    public ResponseEntity<List<Tag>> getAll() {
        return ResponseEntity.ok(tagService.getAll());
    }

    /**
     * Listagem de tags ativas.
     * ✅ Público - usado na seleção de interesses
     */
    @GetMapping("/ativas")
    @Operation(summary = "Listar tags ativas")
    @SecurityRequirements
    public ResponseEntity<List<Tag>> getAtivas() {
        return ResponseEntity.ok(tagService.getAtivas());
    }

    /**
     * Cria uma nova tag. Só ADMIN (SecurityConfig); hoje as tags são cadastradas no banco.
     */
    @PostMapping
    @Operation(summary = "Criar nova tag", description = "Requer role ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Requer role ADMIN")
    })
    public ResponseEntity<Tag> create(@RequestBody @Valid TagRequestDTO dados) {
        String userId = SecurityUtils.getUserIdOrThrow();
        Tag created = tagService.create(new Tag(dados.getNome().trim(), dados.getDescricao(),
                dados.getCategoria().trim(), dados.isAtiva()));
        log.info("[AUDIT] Tag {} criada por userId={}", created.getId(), userId);
        return ResponseEntity.ok(created);
    }

    /**
     * Exclusão de tag: bloqueada (SecurityConfig exige ADMIN e o endpoint não apaga nada).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar tag", description = "Reservado para ADMIN. Temporariamente bloqueado.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN")
    })
    public ResponseEntity<?> delete(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "Acesso negado. Esta operação requer permissão de ADMIN."));
    }

    // ✅ Adicionar logger se não existir
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TagController.class);
}