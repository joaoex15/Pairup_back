package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.service.MatchService;
import com.example.Tinder_ufs.service.PessoaService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("matches")
@AllArgsConstructor
@Tag(name = "Matches", description = "Endpoints para gerenciamento de matches")
public class MatchController {

    private final MatchService matchService;
    private final PessoaService pessoaService;

    /**
     * Lista os matches do usuário autenticado.
     * ✅ pessoaId vem do JWT (req.getAttribute), não da URL — previne IDOR.
     */
    @GetMapping("/meus")
    @Operation(summary = "Listar meus matches")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches retornados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<Match>> listarMeusMatches(HttpServletRequest request) {

        // ✅ userId extraído do JWT pelo JwtFilter — o cliente não pode forjar
        String pessoaId = (String) request.getAttribute("userId");
        if (pessoaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        return ResponseEntity.ok(matchService.listarMeusMatches(pessoaId));
    }

    /**
     * Retorna as redes sociais do outro lado de um match.
     * ✅ Valida que o usuário autenticado faz parte do match antes de retornar os dados.
     */
    @GetMapping("/{matchId}/redes-sociais")
    @Operation(summary = "Buscar redes sociais via match")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redes sociais retornadas"),
            @ApiResponse(responseCode = "403", description = "Sem acesso"),
            @ApiResponse(responseCode = "404", description = "Match não encontrado")
    })
    public ResponseEntity<?> getRedesSociaisDoMatch(
            @Parameter(description = "ID do match")
            @PathVariable String matchId,
            HttpServletRequest request) {

        // ✅ pessoaId vem do JWT — não aceitamos mais como PathVariable
        String pessoaId = (String) request.getAttribute("userId");
        if (pessoaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Match match = matchService.findById(matchId);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }

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
        if (redes == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(redes);
    }

    /**
     * Desfaz um match — apenas quem faz parte pode desfazer.
     */
    @PutMapping("/desfazer/{matchId}")
    @Operation(summary = "Desfazer match")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Match desfeito"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Match não encontrado")
    })
    public ResponseEntity<?> desfazerMatch(
            @Parameter(description = "ID do match")
            @PathVariable String matchId,
            HttpServletRequest request) {

        String pessoaId = (String) request.getAttribute("userId");
        if (pessoaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        Match match = matchService.findById(matchId);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }

        // ✅ Apenas quem faz parte do match pode desfazê-lo
        if (!match.getPessoaId1().equals(pessoaId) &&
                !match.getPessoaId2().equals(pessoaId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Você não faz parte deste match.");
        }

        matchService.desfazerMatch(matchId);
        return ResponseEntity.ok("Match desfeito com sucesso.");
    }
}