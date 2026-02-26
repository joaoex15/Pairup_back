package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Like;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface LikeRepository extends MongoRepository<Like, String> {

    Optional<Like> findByPessoaOrigemIdAndPessoaDestinoId(
            String pessoaOrigemId,
            String pessoaDestinoId
    );

    List<Like> findByPessoaDestinoId(String pessoaDestinoId);
}