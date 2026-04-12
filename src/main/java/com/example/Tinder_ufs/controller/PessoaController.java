package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.PessoaCompletaDTO;
import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pessoas")
@AllArgsConstructor
@Tag(name = "Pessoas", description = "Endpoints para gerenciamento de pessoas")
public class PessoaController {

    private static final Logger log = LoggerFactory.getLogger(PessoaController.class);

    private final PessoaService pessoaService;

    @GetMapping
    @Operation(summary = "Listar perfis com filtros")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfis retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Page<PessoaPerfilDTO>> getAll(
            @RequestParam(required = false) Interesse interesse,
            @RequestParam(required = false) Genero genero,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {

        SecurityUtils.getUserIdOrThrow(request);

        return ResponseEntity.ok(
                pessoaService.getAllPerfisWithFilters(interesse, genero, pageable)
        );
    }

    @GetMapping("/{id}/perfil")
    @Operation(summary = "Buscar perfil da pessoa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<PessoaPerfilDTO> getPerfilById(
            @PathVariable String id,
            HttpServletRequest request) {

        SecurityUtils.getUserIdOrThrow(request);

        PessoaPerfilDTO dto = pessoaService.getPerfilById(id);
        if (dto != null) return ResponseEntity.ok(dto);
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/redes-sociais")
    @Operation(summary = "Buscar redes sociais da pessoa",
            description = "Acesso direto bloqueado. Use GET /matches/{matchId}/redes-sociais.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso direto não permitido")
    })
    public ResponseEntity<Void> getRedesSociaisById(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    @Operation(summary = "Criar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou termos não aceitos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "409", description = "Perfil já existe para este usuário")
    })
    public ResponseEntity<?> create(
            @RequestBody @Valid PessoaPerfilDTO pessoaDTO,  // ✅ MUDOU PARA DTO
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        if (!pessoaDTO.isCienciaResponsabilidade()) {
            log.warn("[AUDIT] Tentativa de criar perfil sem aceitar termos - userId={}", userId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "É necessário aceitar os termos de responsabilidade para criar uma conta"));
        }

        if (pessoaService.findByUsuarioId(userId) != null) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Perfil já existe para este usuário"));
        }

        // Converte DTO para Pessoa
        Pessoa pessoa = new Pessoa();
        pessoa.setNome(pessoaDTO.getNome());
        pessoa.setCurso(pessoaDTO.getCurso());
        pessoa.setDataNasc(pessoaDTO.getDataNasc());
        pessoa.setEmail(pessoaDTO.getEmail());
        pessoa.setGenero(pessoaDTO.getGenero());
        pessoa.setInteresse(pessoaDTO.getInteresse());
        pessoa.setDescricao(pessoaDTO.getDescricao());
        pessoa.setCienciaResponsabilidade(pessoaDTO.isCienciaResponsabilidade());
        pessoa.setUsuarioId(userId);

        try {
            Pessoa criada = pessoaService.create(pessoa);
            log.info("[AUDIT] Perfil criado com sucesso - userId={}", userId);
            return ResponseEntity.ok(criada);
        } catch (RuntimeException e) {
            log.error("[AUDIT] Erro ao criar perfil - userId={}, erro={}", userId, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Obter meu perfil completo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil completo retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public ResponseEntity<PessoaCompletaDTO> getMyProfile(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        PessoaCompletaDTO pessoaCompleta = pessoaService.getPessoaCompletaByUsuarioId(userId);
        if (pessoaCompleta == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("[AUDIT] Perfil completo acessado - userId={}", userId);
        return ResponseEntity.ok(pessoaCompleta);
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public ResponseEntity<?> updateMe(
            @RequestBody @Valid PessoaPerfilDTO pessoaDTO,  // ✅ MUDOU PARA DTO
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa existing = pessoaService.findByUsuarioId(userId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        // Atualiza só os campos que vieram
        if (pessoaDTO.getNome() != null) existing.setNome(pessoaDTO.getNome());
        if (pessoaDTO.getCurso() != null) existing.setCurso(pessoaDTO.getCurso());
        if (pessoaDTO.getDataNasc() != null) existing.setDataNasc(pessoaDTO.getDataNasc());
        if (pessoaDTO.getGenero() != null) existing.setGenero(pessoaDTO.getGenero());
        if (pessoaDTO.getInteresse() != null) existing.setInteresse(pessoaDTO.getInteresse());
        if (pessoaDTO.getDescricao() != null) existing.setDescricao(pessoaDTO.getDescricao());

        // ✅ IGNORA tags por enquanto para não causar erro
        // (se precisar atualizar tags, faz depois)

        try {
            Pessoa atualizada = pessoaService.update(existing);
            log.info("[AUDIT] Perfil atualizado - userId={}", userId);
            return ResponseEntity.ok(atualizada);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/me/ciencia-responsabilidade")
    @Operation(summary = "Marcar ciência de responsabilidade")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marcado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public ResponseEntity<?> marcarCienciaResponsabilidade(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Pessoa atualizada = pessoaService.marcarCienciaResponsabilidade(pessoa.getId());
            log.info("[AUDIT] Ciência de responsabilidade marcada - userId={}", userId);
            return ResponseEntity.ok(atualizada);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deletar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Perfil deletado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Void> deleteMe(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa != null) {
            log.info("[AUDIT] Perfil deletado - userId={}", userId);
            pessoaService.delete(pessoa.getId());
        }
        return ResponseEntity.noContent().build();
    }
}