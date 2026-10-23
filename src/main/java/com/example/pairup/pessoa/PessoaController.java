package com.example.pairup.pessoa;

import com.example.pairup.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            @PageableDefault(size = 20) Pageable pageable) {

        SecurityUtils.getUserIdOrThrow();

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
            @PathVariable String id) {

        SecurityUtils.getUserIdOrThrow();

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
            @RequestBody @Valid PessoaRequestDTO pessoaDTO) {

        String userId = SecurityUtils.getUserIdOrThrow();

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
        pessoa.setInstagram(semTexto(pessoaDTO.getInstagram()));
        pessoa.setWhatsapp(semTexto(pessoaDTO.getWhatsapp()));
        pessoa.setTelegram(semTexto(pessoaDTO.getTelegram()));
        // Fora do try: tag inválida sai como 400 pelo GlobalExceptionHandler
        pessoa.setTags(pessoaService.resolverTags(pessoaDTO.getTags()));
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
    public ResponseEntity<PessoaCompletaDTO> getMyProfile() {
        String userId = SecurityUtils.getUserIdOrThrow();

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
            @RequestBody @Valid PessoaRequestDTO pessoaDTO) {

        String userId = SecurityUtils.getUserIdOrThrow();

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
        // String vazia apaga a rede social; campo ausente mantém o valor atual
        if (pessoaDTO.getInstagram() != null) existing.setInstagram(semTexto(pessoaDTO.getInstagram()));
        if (pessoaDTO.getWhatsapp() != null) existing.setWhatsapp(semTexto(pessoaDTO.getWhatsapp()));
        if (pessoaDTO.getTelegram() != null) existing.setTelegram(semTexto(pessoaDTO.getTelegram()));
        // Lista vazia remove todas as tags; campo ausente mantém as atuais
        if (pessoaDTO.getTags() != null) existing.setTags(pessoaService.resolverTags(pessoaDTO.getTags()));

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
    public ResponseEntity<?> marcarCienciaResponsabilidade() {
        String userId = SecurityUtils.getUserIdOrThrow();

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

    private static String semTexto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deletar meu perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Perfil deletado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Void> deleteMe() {
        String userId = SecurityUtils.getUserIdOrThrow();

        Pessoa pessoa = pessoaService.findByUsuarioId(userId);
        if (pessoa != null) {
            log.info("[AUDIT] Perfil deletado - userId={}", userId);
            pessoaService.delete(pessoa.getId());
        }
        return ResponseEntity.noContent().build();
    }
}