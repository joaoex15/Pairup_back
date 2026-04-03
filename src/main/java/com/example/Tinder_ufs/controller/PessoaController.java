package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("pessoas")
@AllArgsConstructor
@Tag(name = "Pessoas", description = "Endpoints para gerenciamento de pessoas")
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    @Operation(summary = "Listar perfis com filtros")
    public ResponseEntity<List<PessoaPerfilDTO>> getAll(
            @RequestParam(required = false) Interesse interesse,
            @RequestParam(required = false) Genero genero) {

        return ResponseEntity.ok(
                pessoaService.getAllPerfisWithFilters(interesse, genero)
        );
    }

    @GetMapping("/{id}/perfil")
    @Operation(summary = "Buscar perfil da pessoa")
    public ResponseEntity<PessoaPerfilDTO> getPerfilById(@PathVariable String id){
        PessoaPerfilDTO dto = pessoaService.getPerfilById(id);
        if (dto != null) return ResponseEntity.ok(dto);
        return ResponseEntity.notFound().build();
    }

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

    @GetMapping("/me")
    @Operation(summary = "Obter meu perfil")
    public ResponseEntity<PessoaPerfilDTO> getMyProfile(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(pessoaService.getPerfilById(pessoa.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar meu perfil")
    public ResponseEntity<Pessoa> updateMe(@RequestBody @Valid Pessoa pessoa, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa existing = pessoaService.findByUsuarioId(userId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        pessoa.setId(existing.getId());
        Pessoa updated = pessoaService.update(pessoa);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/me/ciencia-responsabilidade")
    @Operation(summary = "Marcar ciência de responsabilidade")
    public ResponseEntity<Pessoa> marcarCienciaResponsabilidade(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(pessoaService.marcarCienciaResponsabilidade(pessoa.getId()));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deletar meu perfil")
    public ResponseEntity<?> deleteMe(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa != null) {
            pessoaService.delete(pessoa.getId());
        }
        return ResponseEntity.noContent().build();
    }
}