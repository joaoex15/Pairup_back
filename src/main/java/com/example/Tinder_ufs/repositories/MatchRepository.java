package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface MatchRepository extends MongoRepository<Match, String> {

    /**
     * ✅ CORRIGIDO: substituído findByPessoaId1OrPessoaId2AndAtivoTrue que tinha
     *    precedência ambígua no Spring Data (interpretava como pessoaId1 OR (pessoaId2 AND ativo)).
     *    A @Query abaixo garante: ativo=true AND (pessoaId1=x OR pessoaId2=x).
     */
    @Query("{ '$and': [ { 'ativo': true }, { '$or': [ { 'pessoaId1': ?0 }, { 'pessoaId2': ?1 } ] } ] }")
    Page<Match> findMeusMatchesAtivos(String pessoaId1, String pessoaId2, Pageable pageable);

    Optional<Match> findByPessoaId1AndPessoaId2(String pessoaId1, String pessoaId2);

    boolean existsByPessoaId1AndPessoaId2AndAtivoTrue(String pessoaId1, String pessoaId2);
}