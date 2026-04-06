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
    public ResponseEntity<List<Tag>> getAll() {
        return ResponseEntity.ok(tagService.getAll());
    }

    /**
     * Listagem de tags ativas.
     * ✅ Público - usado na seleção de interesses
     */
    @GetMapping("/ativas")
    @Operation(summary = "Listar tags ativas")
    public ResponseEntity<List<Tag>> getAtivas() {
        return ResponseEntity.ok(tagService.getAtivas());
    }

    /**
     * Cria uma nova tag.
     * ✅ CORRIGIDO: Agora exige autenticação e retorna 401 se não autenticado
     */
    @PostMapping
    @Operation(summary = "Criar nova tag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> create(
            @RequestBody @Valid Tag tag,
            HttpServletRequest request) {

        try {
            // ✅ CORRIGIDO: Valida autenticação e retorna 401 se falhar
            String userId = SecurityUtils.getUserIdOrThrow(request);

            // Opcional: Log de quem criou a tag
            log.info("Tag criada por userId={}: {}", userId, tag.getNome());

            Tag created = tagService.create(tag);
            return ResponseEntity.ok(created);

        } catch (Exception e) {
            // ✅ CORRIGIDO: Retorna 401 explícito em vez de 200
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Autenticação necessária para criar tags"));
        }
    }

    /**
     * Exclusão de tag.
     * ✅ Retorna 403 explícito (bloqueado para não-ADMIN)
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar tag", description = "Reservado para ADMIN. Temporariamente bloqueado.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN")
    })
    public ResponseEntity<?> delete(@PathVariable String id, HttpServletRequest request) {
        // ✅ CORRIGIDO: Verifica autenticação antes de negar
        try {
            String userId = SecurityUtils.getUserIdOrThrow(request);
            log.warn("Tentativa de deletar tag {} por userId={} - BLOQUEADO (requer ADMIN)", id, userId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Autenticação necessária"));
        }

        // ✅ Retorna 403 Forbidden (não 401)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "Acesso negado. Esta operação requer permissão de ADMIN."));
    }

    // ✅ Adicionar logger se não existir
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TagController.class);
}