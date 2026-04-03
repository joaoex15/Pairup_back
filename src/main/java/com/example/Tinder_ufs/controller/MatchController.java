package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.MatchService;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("matches")
@AllArgsConstructor
@Tag(name = "Matches", description = "Endpoints para gerenciamento de matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private final MatchService matchService;
    private final PessoaService pessoaService;

    /**
     * Lista os matches do usuário autenticado.
     * ✅ pessoaId vem do JWT — previne IDOR.
     * ✅ Paginado — evita OOM com muitos matches.
     */
    @GetMapping("/meus")
    @Operation(summary = "Listar meus matches")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Page<Match>> listarMeusMatches(
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {

        String pessoaId = SecurityUtils.getUserIdOrThrow(request);
        return ResponseEntity.ok(matchService.listarMeusMatches(pessoaId, pageable));
    }

    /**
     * Retorna as redes sociais do outro lado de um match.
     * ✅ Valida que o solicitante faz parte do match antes de retornar os dados.
     * ✅ Valida que o match está ativo.
     */
    @GetMapping("/{matchId}/redes-sociais")
    @Operation(summary = "Buscar redes sociais via match")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redes sociais retornadas"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem acesso ou match inativo"),
            @ApiResponse(responseCode = "404", description = "Match não encontrado")
    })
    public ResponseEntity<?> getRedesSociaisDoMatch(
            @Parameter(description = "ID do match")
            @PathVariable String matchId,
            HttpServletRequest request) {

        String pessoaId = SecurityUtils.getUserIdOrThrow(request);

        Match match = matchService.findById(matchId);
        if (match == null) return ResponseEntity.notFound().build();

        if (!match.isAtivo()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Match inativo.");
        }

        // ✅ Garante que o solicitante faz parte do match
        if (!match.getPessoaId1().equals(pessoaId) &&
                !match.getPessoaId2().equals(pessoaId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Você não faz parte deste match.");
        }

        String outraPessoaId = match.getPessoaId1().equals(pessoaId)
                ? match.getPessoaId2()
                : match.getPessoaId1();

        PessoaRedesSociaisDTO redes = pessoaService.getRedesSociaisById(outraPessoaId);
        if (redes == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(redes);
    }

    /**
     * Desfaz um match — apenas quem faz parte pode desfazer.
     * ✅ Log de auditoria registra quem desfez e qual match.
     */
    @PutMapping("/desfazer/{matchId}")
    @Operation(summary = "Desfazer match")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Match desfeito"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Match não encontrado")
    })
    public ResponseEntity<?> desfazerMatch(
            @Parameter(description = "ID do match")
            @PathVariable String matchId,
            HttpServletRequest request) {

        String pessoaId = SecurityUtils.getUserIdOrThrow(request);

        Match match = matchService.findById(matchId);
        if (match == null) return ResponseEntity.notFound().build();

        // ✅ Apenas quem faz parte do match pode desfazê-lo
        if (!match.getPessoaId1().equals(pessoaId) &&
                !match.getPessoaId2().equals(pessoaId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Você não faz parte deste match.");
        }

        matchService.desfazerMatch(matchId);
        log.info("[AUDIT] Match desfeito matchId={} por pessoaId={}", matchId, pessoaId);
        return ResponseEntity.ok("Match desfeito com sucesso.");
    }
}