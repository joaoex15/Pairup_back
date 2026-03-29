package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    // 🔓 Pode continuar público (tipo feed do Tinder)
    @GetMapping
    @Operation(summary = "Listar perfis com filtros")
    public ResponseEntity<List<PessoaPerfilDTO>> getAll(
            @RequestParam(required = false) Interesse interesse,
            @RequestParam(required = false) Genero genero) {

        return ResponseEntity.ok(
                pessoaService.getAllPerfisWithFilters(interesse, genero)
        );
    }

    // 🔓 Pode ser público (perfil básico)
    @GetMapping("/{id}/perfil")
    @Operation(summary = "Buscar perfil da pessoa")
    public ResponseEntity<PessoaPerfilDTO> getPerfilById(@PathVariable String id){
        PessoaPerfilDTO dto = pessoaService.getPerfilById(id);
        if (dto != null) return ResponseEntity.ok(dto);
        return ResponseEntity.notFound().build();
    }

    // 🔒 BLOQUEADO (até implementar match + JWT)
    @GetMapping("/{id}/redes-sociais")
    @Operation(summary = "Buscar redes sociais da pessoa")
    public ResponseEntity<?> getRedesSociaisById(@PathVariable String id){
        throw new RuntimeException("Acesso negado: requer match + autenticação");
    }

    @PostMapping
    @Operation(summary = "Criar nova pessoa")
    public ResponseEntity<Pessoa> create(@RequestBody @Valid Pessoa pessoa){
        try {
            return ResponseEntity.ok(pessoaService.create(pessoa));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 🔒 BLOQUEADO até autenticação real
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pessoa")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody @Valid Pessoa pessoa){
        throw new RuntimeException("Endpoint desabilitado até autenticação (JWT)");
    }

    // 🔒 BLOQUEADO até autenticação real
    @PatchMapping("/{id}/ciencia-responsabilidade")
    @Operation(summary = "Marcar ciência de responsabilidade")
    public ResponseEntity<?> marcarCienciaResponsabilidade(@PathVariable String id){
        throw new RuntimeException("Endpoint desabilitado até autenticação (JWT)");
    }

    // 🔒 BLOQUEADO até autenticação real
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar pessoa")
    public ResponseEntity<?> delete(@PathVariable String id){
        throw new RuntimeException("Endpoint desabilitado até autenticação (JWT)");
    }
}