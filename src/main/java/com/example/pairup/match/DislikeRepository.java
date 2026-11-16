package com.example.pairup.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DislikeRepository extends MongoRepository<Dislike, String> {

    Page<Dislike> findByPessoaOrigemId(String pessoaOrigemId, Pageable pageable);

    boolean existsByPessoaOrigemIdAndPessoaDestinoId(String pessoaOrigemId, String pessoaDestinoId);

    /** Apaga os dislikes dados e recebidos por uma pessoa (exclusão de perfil). */
    long deleteByPessoaOrigemIdOrPessoaDestinoId(String pessoaOrigemId, String pessoaDestinoId);
}
