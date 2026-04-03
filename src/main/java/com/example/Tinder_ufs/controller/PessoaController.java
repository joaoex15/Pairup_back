package com.example.Tinder_ufs.controller;

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

@RestController
@RequestMapping("pessoas")
@AllArgsConstructor
@Tag(name = "Pessoas", description = "Endpoints para gerenciamento de pessoas")
public class PessoaController {

    private static final Logger log = LoggerFactory.getLogger(PessoaController.class);

    private final PessoaService pessoaService;

    /**
     * Lista perfis com filtros.
     * ✅ Exige JWT — perfis não devem ser expostos publicamente.
     * ✅ Paginado — evita OOM com muitos registros.
     */
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

        // ✅ Exige autenticação para ver perfis
        SecurityUtils.getUserIdOrThrow(request);

        return ResponseEntity.ok(
                pessoaService.getAllPerfisWithFilters(interesse, genero, pageable)
        );
    }

    /**
     * Busca perfil público de uma pessoa.
     * ✅ Exige JWT — informações pessoais só para usuários autenticados.
     */
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

        // ✅ Exige autenticação
        SecurityUtils.getUserIdOrThrow(request);

        PessoaPerfilDTO dto = pessoaService.getPerfilById(id);
        if (dto != null) return ResponseEntity.ok(dto);
        return ResponseEntity.notFound().build();
    }

    /**
     * Redes sociais — disponível APENAS via /matches/{matchId}/redes-sociais.
     * ✅ Retorna 403 explícito em vez de lançar RuntimeException.
     *    RuntimeException pode vazar stack trace; ResponseEntity não.
     */
    @GetMapping("/{id}/redes-sociais")
    @Operation(summary = "Buscar redes sociais da pessoa",
            description = "Acesso direto bloqueado. Use GET /matches/{matchId}/redes-sociais.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Acesso direto não permitido")
    })
    public ResponseEntity<Void> getRedesSociaisById(@PathVariable String id) {
        // ✅ 403 explícito — nunca RuntimeException que poderia vazar detalhes internos
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Cria perfil vinculado ao usuário autenticado.
     * ✅ Exige JWT — impede criação de perfis em massa por bots.
     * ✅ userId vem do token — o cliente nunca pode vincular o perfil a outro usuário.
     * ✅ Log de auditoria registra criação de perfil.
     */
    @PostMapping
    @Operation(summary = "Criar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "409", description = "Perfil já existe para este usuário")
    })
    public ResponseEntity<Pessoa> create(
            @RequestBody @Valid Pessoa pessoa,
            HttpServletRequest request) {

        // ✅ userId vem do JWT — nunca do corpo da requisição
        String userId = SecurityUtils.getUserIdOrThrow(request);

        // Previne que o cliente tente setar o usuarioId manualmente
        pessoa.setUsuarioId(userId);

        Pessoa criada = pessoaService.create(pessoa);
        log.info("[AUDIT] Perfil criado para userId={}", userId);
        return ResponseEntity.ok(criada);
    }

    /** Retorna o perfil do usuário autenticado. */
    @GetMapping("/me")
    @Operation(summary = "Obter meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public ResponseEntity<PessoaPerfilDTO> getMyProfile(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(pessoaService.getPerfilById(pessoa.getId()));
    }

    /** Atualiza o perfil do usuário autenticado. */
    @PutMapping("/me")
    @Operation(summary = "Atualizar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado")
    })
    public ResponseEntity<Pessoa> updateMe(
            @RequestBody @Valid Pessoa pessoa,
            HttpServletRequest request) {

        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa existing = pessoaService.findByUsuarioId(userId);
        if (existing == null) return ResponseEntity.notFound().build();

        // ✅ Garante que o ID não pode ser substituído pelo corpo da requisição
        pessoa.setId(existing.getId());
        pessoa.setUsuarioId(userId);

        return ResponseEntity.ok(pessoaService.update(pessoa));
    }

    /** Marca ciência de responsabilidade do usuário autenticado. */
    @PatchMapping("/me/ciencia-responsabilidade")
    @Operation(summary = "Marcar ciência de responsabilidade")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marcado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Pessoa> marcarCienciaResponsabilidade(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(pessoaService.marcarCienciaResponsabilidade(pessoa.getId()));
    }

    /**
     * Deleta o perfil do usuário autenticado.
     * ✅ Log de auditoria antes da exclusão.
     */
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
            log.info("[AUDIT] Perfil deletado para userId={}", userId);
            pessoaService.delete(pessoa.getId());
        }
        return ResponseEntity.noContent().build();
    }
}