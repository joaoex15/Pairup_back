package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PessoaRepository extends MongoRepository<Pessoa, String> {

    /**
     * ✅ CORRIGIDO: filtros e paginação delegados ao MongoDB.
     *    Antes havia apenas findByAtivoTrue() sem Pageable, carregando tudo em memória.
     */
    Page<Pessoa> findByAtivoTrue(Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndInteresse(Interesse interesse, Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndGenero(Genero genero, Pageable pageable);

    Page<Pessoa> findByAtivoTrueAndInteresseAndGenero(Interesse interesse, Genero genero, Pageable pageable);

    Optional<Pessoa> findByUsuarioId(String usuarioId);

    boolean existsByEmail(String email);

    boolean existsByUsuarioId(String usuarioId);
}