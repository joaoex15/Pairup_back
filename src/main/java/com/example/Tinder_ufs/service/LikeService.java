package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.LikeRepository;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;

    // ✅ LISTAR LIKES DADOS (ORIGEM)
    public List<Like> listarLikesDados(String pessoaId) {
        return likeRepository.findAll().stream()
                .filter(like -> like.getPessoaOrigemId().equals(pessoaId))
                .collect(Collectors.toList());
    }

    // ✅ LISTAR LIKES RECEBIDOS (DESTINO)
    public List<Like> listarLikesRecebidos(String pessoaId) {
        return likeRepository.findAll().stream()
                .filter(like -> like.getPessoaDestinoId().equals(pessoaId))
                .collect(Collectors.toList());
    }

    // ✅ VERIFICAR SE JÁ DEU LIKE
    private boolean jaDeuLike(String origemId, String destinoId) {
        return listarLikesDados(origemId).stream()
                .anyMatch(like -> like.getPessoaDestinoId().equals(destinoId) && like.isAtivo());
    }

    // ✅ VERIFICAR SE RECEBEU LIKE (like reverso)
    private boolean recebeuLike(String origemId, String destinoId) {
        return listarLikesRecebidos(origemId).stream()
                .anyMatch(like -> like.getPessoaOrigemId().equals(destinoId) && like.isAtivo());
    }

    // ✅ CRIAR OU ATIVAR LIKE
    private void criarOuAtivarLike(String origemId, String destinoId) {
        Optional<Like> likeExistente = likeRepository
                .findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId);

        if (likeExistente.isPresent()) {
            if (!likeExistente.get().isAtivo()) {
                likeExistente.get().setAtivo(true);
                likeRepository.save(likeExistente.get());
            }
        } else {
            Like novoLike = new Like();
            novoLike.setPessoaOrigemId(origemId);
            novoLike.setPessoaDestinoId(destinoId);
            novoLike.setAtivo(true);
            likeRepository.save(novoLike);
        }
    }

    // ✅ CRIAR MATCH SE NÃO EXISTIR
    private void criarMatchSeNaoExistir(String pessoa1, String pessoa2) {
        Optional<Match> matchExistente = matchRepository
                .findByPessoaId1AndPessoaId2(pessoa1, pessoa2);

        if (matchExistente.isEmpty()) {
            Match match = new Match();
            match.setPessoaId1(pessoa1);
            match.setPessoaId2(pessoa2);
            match.setAtivo(true);
            matchRepository.save(match);
            System.out.println("💕 MATCH criado entre " + pessoa1 + " e " + pessoa2);
        }
    }

    // ✅ MÉTODO PRINCIPAL (agora mais limpo!)
    public void darLike(String origemId, String destinoId) {
        if (origemId.equals(destinoId)) {
            throw new RuntimeException("Não pode dar like em si mesmo");
        }

        if (jaDeuLike(origemId, destinoId)) {
            throw new RuntimeException("Você já curtiu essa pessoa");
        }

        // Cria ou ativa o like
        criarOuAtivarLike(origemId, destinoId);

        // Verifica se a outra pessoa já deu like (like reverso)
        if (recebeuLike(origemId, destinoId)) {
            criarMatchSeNaoExistir(origemId, destinoId);
        }
    }
}