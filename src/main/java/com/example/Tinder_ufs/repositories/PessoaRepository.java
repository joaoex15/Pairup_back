package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PessoaRepository extends MongoRepository<Pessoa, String> {

    Page<Pessoa> findByAtivoTrue(Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndInteresse(Interesse interesse, Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndGenero(Genero genero, Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndInteresseAndGenero(Interesse interesse, Genero genero, Pageable pageable);

    Optional<Pessoa> findByUsuarioId(String usuarioId);

    Optional<Pessoa> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsuarioId(String usuarioId);
}