package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends MongoRepository<Match, String> {

    // Métodos existentes
    List<Match> findByPessoaId1OrPessoaId2(String pessoaId1, String pessoaId2);

    Optional<Match> findByPessoaId1AndPessoaId2(String pessoaId1, String pessoaId2);

    // ✅ NOVOS MÉTODOS PARA O SERVICE

    // Busca matches ativos com paginação (usado no MatchService.listarMeusMatches)
    @Query("{ $and: [ { $or: [ {pessoaId1: ?0}, {pessoaId2: ?0} ] }, { ativo: true } ] }")
    Page<Match> findByPessoaId1OrPessoaId2AndAtivoTrue(String pessoaId1, String pessoaId2, Pageable pageable);

    // Verifica se existe match ativo entre duas pessoas (usado no MatchService.existeMatchAtivo)
    boolean existsByPessoaId1AndPessoaId2AndAtivoTrue(String pessoaId1, String pessoaId2);

    // ✅ MÉTODOS ADICIONAIS ÚTEIS

    // Busca apenas matches ativos de uma pessoa (sem paginação)
    @Query("{ $and: [ { $or: [ {pessoaId1: ?0}, {pessoaId2: ?0} ] }, { ativo: true } ] }")
    List<Match> findActiveMatchesByPessoaId(String pessoaId);

    // Busca match específico e ativo entre duas pessoas
    @Query("{ $and: [ { $or: [ {pessoaId1: ?0, pessoaId2: ?1}, {pessoaId1: ?1, pessoaId2: ?0} ] }, { ativo: true } ] }")
    Optional<Match> findActiveMatchBetween(String pessoaId1, String pessoaId2);

    // Conta quantos matches ativos uma pessoa tem
    @Query(value = "{ $and: [ { $or: [ {pessoaId1: ?0}, {pessoaId2: ?0} ] }, { ativo: true } ] }", count = true)
    long countActiveMatchesByPessoaId(String pessoaId);
}