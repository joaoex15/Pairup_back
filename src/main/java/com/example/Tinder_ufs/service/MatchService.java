package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;

    public List<Match> listarMeusMatches(String pessoaId){
        return matchRepository.findByPessoaId1OrPessoaId2(pessoaId, pessoaId).stream()
                .filter(Match::isAtivo)
                .collect(Collectors.toList());
    }

    public Match findById(String id){
        return matchRepository.findById(id).orElse(null);
    }

    public void desfazerMatch(String id){
        Match match = matchRepository.findById(id).orElse(null);

        if(match != null){
            match.setAtivo(false);
            matchRepository.save(match);
        }
    }
}