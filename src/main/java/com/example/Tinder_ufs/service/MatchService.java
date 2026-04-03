package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.repositories.MatchRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;

    public Page<Match> listarMeusMatches(String pessoaId, Pageable pageable) {
        return matchRepository.findByPessoaId1OrPessoaId2AndAtivoTrue(pessoaId, pessoaId, pageable);
    }

    public Match findById(String id) {
        return matchRepository.findById(id).orElse(null);
    }

    public void desfazerMatch(String id) {
        Match match = matchRepository.findById(id).orElse(null);
        if (match != null) {
            match.setAtivo(false);
            matchRepository.save(match);
        }
    }

    public boolean existeMatchAtivo(String pessoaId1, String pessoaId2) {
        return matchRepository.existsByPessoaId1AndPessoaId2AndAtivoTrue(pessoaId1, pessoaId2) ||
                matchRepository.existsByPessoaId1AndPessoaId2AndAtivoTrue(pessoaId2, pessoaId1);
    }
}