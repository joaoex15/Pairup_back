package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.LikeRepository;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;

    public Page<Like> listarLikesDados(String pessoaId, Pageable pageable) {
        List<Like> likes = likeRepository.findByPessoaOrigemId(pessoaId).stream()
                .filter(Like::isAtivo)
                .collect(Collectors.toList());

        return applyPagination(likes, pageable);
    }

    public Page<Like> listarLikesRecebidos(String pessoaId, Pageable pageable) {
        List<Like> likes = likeRepository.findByPessoaDestinoId(pessoaId).stream()
                .filter(Like::isAtivo)
                .collect(Collectors.toList());

        return applyPagination(likes, pageable);
    }

    private Page<Like> applyPagination(List<Like> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());

        if (start > list.size()) {
            return Page.empty(pageable);
        }

        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }

    private boolean jaDeuLike(String origemId, String destinoId) {
        return likeRepository
                .findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId)
                .map(Like::isAtivo)
                .orElse(false);
    }

    private boolean existeLikeReverso(String origemId, String destinoId) {
        return likeRepository
                .findByPessoaOrigemIdAndPessoaDestinoId(destinoId, origemId)
                .map(Like::isAtivo)
                .orElse(false);
    }

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

    private void criarMatchSeNaoExistir(String pessoa1, String pessoa2) {
        boolean jaExiste = matchRepository.findByPessoaId1AndPessoaId2(pessoa1, pessoa2).isPresent() ||
                matchRepository.findByPessoaId1AndPessoaId2(pessoa2, pessoa1).isPresent();

        if (!jaExiste) {
            Match match = new Match();
            match.setPessoaId1(pessoa1);
            match.setPessoaId2(pessoa2);
            match.setAtivo(true);
            matchRepository.save(match);
        }
    }

    public Map<String, Object> darLike(String origemId, String destinoId) {
        if (origemId.equals(destinoId)) {
            throw new RuntimeException("Não pode dar like em si mesmo");
        }

        if (jaDeuLike(origemId, destinoId)) {
            throw new RuntimeException("Você já curtiu essa pessoa");
        }

        criarOuAtivarLike(origemId, destinoId);

        boolean isMatch = existeLikeReverso(origemId, destinoId);

        Map<String, Object> response = new HashMap<>();

        if (isMatch) {
            criarMatchSeNaoExistir(origemId, destinoId);
            response.put("match", true);
            response.put("message", "É um match!");
        } else {
            response.put("match", false);
            response.put("message", "Like enviado com sucesso");
        }

        return response;
    }
}