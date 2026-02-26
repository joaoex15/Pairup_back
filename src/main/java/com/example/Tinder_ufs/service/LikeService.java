package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.LikeRepository;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;

    public void darLike(String origemId, String destinoId){

        if(origemId.equals(destinoId)){
            throw new RuntimeException("Não pode dar like em si mesmo");
        }

        // 🔎 Verifica se já existe like
        Optional<Like> likeExistente =
                likeRepository.findByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId);

        if(likeExistente.isPresent()){
            if(likeExistente.get().isAtivo()){
                throw new RuntimeException("Você já curtiu essa pessoa");
            } else {
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

        // 🔎 Verifica like reverso
        Optional<Like> likeReverso =
                likeRepository.findByPessoaOrigemIdAndPessoaDestinoId(destinoId, origemId);

        if(likeReverso.isPresent() && likeReverso.get().isAtivo()) {

            // 🔎 Verifica se já existe match
            Optional<Match> matchExistente =
                    matchRepository.findByPessoaId1AndPessoaId2(origemId, destinoId);

            if(matchExistente.isEmpty()){
                Match match = new Match();
                match.setPessoaId1(origemId);
                match.setPessoaId2(destinoId);
                match.setAtivo(true);

                matchRepository.save(match);
            }
        }
    }
}