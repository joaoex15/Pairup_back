package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Match;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends MongoRepository<Match, String> {

    List<Match> findByPessoaId1OrPessoaId2(String pessoaId1, String pessoaId2);

    Optional<Match> findByPessoaId1AndPessoaId2(
            String pessoaId1,
            String pessoaId2
    );
}