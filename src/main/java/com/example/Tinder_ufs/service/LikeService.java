package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.LikeRepository;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;

    // ── Consultas públicas ──────────────────────────────────────────────────

    public List<Like> listarLikesDados(String pessoaId) {
        return likeRepository.findByPessoaOrigemId(pessoaId).stream()
                .filter(Like::isAtivo)
                .collect(Collectors.toList());
    }

    public List<Like> listarLikesRecebidos(String pessoaId) {
        return likeRepository.findByPessoaDestinoId(pessoaId).stream()
                .filter(Like::isAtivo)
                .collect(Collectors.toList());
    }

    // ── Helpers privados ────────────────────────────────────────────────────

    /** Verifica se origemId já deu like ativo em destinoId */
    private boolean jaDeuLike(String origemId, String destinoId) {
        return likeRepository
                .findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId)
                .map(Like::isAtivo)
                .orElse(false);
    }

    /** Verifica se destinoId já deu like ativo em origemId (like reverso) */
    private boolean existeLikeReverso(String origemId, String destinoId) {
        return likeRepository
                .findByPessoaOrigemIdAndPessoaDestinoId(destinoId, origemId)
                .map(Like::isAtivo)
                .orElse(false);
    }

    /** Cria um novo like ou reativa um like inativo já existente */
    private void criarOuAtivarLike(String origemId, String destinoId) {
        Optional<Like> likeExistente =
                likeRepository.findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId);

        if (likeExistente.isPresent()) {
            Like like = likeExistente.get();
            if (!like.isAtivo()) {
                like.setAtivo(true);
                likeRepository.save(like);
            }
        } else {
            Like novoLike = new Like();
            novoLike.setPessoaOrigemId(origemId);
            novoLike.setPessoaDestinoId(destinoId);
            novoLike.setAtivo(true);
            likeRepository.save(novoLike);
        }
    }

    /**
     * Cria o match verificando as duas ordens possíveis (pessoa1↔pessoa2)
     * para evitar duplicatas independente de quem deu o like primeiro.
     */
    private void criarMatchSeNaoExistir(String pessoa1, String pessoa2) {
        boolean jaExiste =
                matchRepository.findByPessoaId1AndPessoaId2(pessoa1, pessoa2).isPresent() ||
                        matchRepository.findByPessoaId1AndPessoaId2(pessoa2, pessoa1).isPresent();

        if (!jaExiste) {
            Match match = new Match();
            match.setPessoaId1(pessoa1);
            match.setPessoaId2(pessoa2);
            match.setAtivo(true);
            matchRepository.save(match);
            System.out.println("💕 MATCH criado entre " + pessoa1 + " e " + pessoa2);
        }
    }

    // ── Método principal ────────────────────────────────────────────────────

    public void darLike(String origemId, String destinoId) {
        if (origemId.equals(destinoId))
            throw new RuntimeException("Não pode dar like em si mesmo");

        if (jaDeuLike(origemId, destinoId))
            throw new RuntimeException("Você já curtiu essa pessoa");

        criarOuAtivarLike(origemId, destinoId);

        // Se a outra pessoa já havia dado like, é match!
        if (existeLikeReverso(origemId, destinoId)) {
            criarMatchSeNaoExistir(origemId, destinoId);
        }
    }
}