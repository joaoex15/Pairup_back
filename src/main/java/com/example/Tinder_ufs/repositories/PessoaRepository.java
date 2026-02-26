package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PessoaRepository extends MongoRepository<Pessoa, String> {

    Optional<Pessoa> findByEmail(String email);

    List<Pessoa> findByAtivoTrue();

    List<Pessoa> findByGenero(String genero);
}