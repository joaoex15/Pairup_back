package com.example.pairup.match;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;

    /**
     * ✅ CORRIGIDO: paginação delegada ao repositório.
     *    Antes carregava todos os likes em memória e paginava em Java — risco de OOM.
     */
    public Page<Like> listarLikesDados(String pessoaId, Pageable pageable) {
        return likeRepository.findByPessoaOrigemIdAndAtivoTrue(pessoaId, pageable);
    }

    /**
     * ✅ CORRIGIDO: paginação delegada ao repositório.
     */
    public Page<Like> listarLikesRecebidos(String pessoaId, Pageable pageable) {
        return likeRepository.findByPessoaDestinoIdAndAtivoTrue(pessoaId, pageable);
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
        boolean jaExiste =
                matchRepository.findByPessoaId1AndPessoaId2(pessoa1, pessoa2).isPresent() ||
                        matchRepository.findByPessoaId1AndPessoaId2(pessoa2, pessoa1).isPresent();

        if (!jaExiste) {
            Match match = new Match();
            match.setPessoaId1(pessoa1);
            match.setPessoaId2(pessoa2);
            match.setAtivo(true);
            matchRepository.save(match);
        }
    }

    /**
     * ✅ CORRIGIDO: adicionado @Transactional para tornar verificar+criar atômico.
     *    Sem isso, dois requests simultâneos podiam passar pela checagem jaDeuLike
     *    ao mesmo tempo — race condition antes do índice único do MongoDB barrar.
     */
    @Transactional
    public Map<String, Object> darLike(String origemId, String destinoId) {
        if (jaDeuLike(origemId, destinoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você já curtiu essa pessoa");
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