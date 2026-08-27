package com.example.pairup.repositories;

import com.example.pairup.models.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {

    /**
     * ✅ CORRIGIDO: paginação nativa no banco.
     *    Antes o LikeService buscava todos os likes em memória e paginava em Java — risco de OOM.
     */
    Page<Like> findByPessoaOrigemIdAndAtivoTrue(String pessoaOrigemId, Pageable pageable);

    Page<Like> findByPessoaDestinoIdAndAtivoTrue(String pessoaDestinoId, Pageable pageable);

    Optional<Like> findByPessoaOrigemIdAndPessoaDestinoId(String pessoaOrigemId, String pessoaDestinoId);
}