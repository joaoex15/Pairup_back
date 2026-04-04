package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImagemRepository extends MongoRepository<Imagem, String> {

    Optional<Imagem> findByPublicId(String publicId);

    Optional<Imagem> findByPessoaAndPerfilTrue(Pessoa pessoa);

    List<Imagem> findByPessoaAndAtivaTrue(Pessoa pessoa);

    @Query("{ 'pessoa': ?0, 'ativa': true }")
    List<Imagem> findActiveImagesByPessoa(Pessoa pessoa);

    long countByPessoaAndAtivaTrue(Pessoa pessoa);

    @Query("{ 'pessoa': ?0, 'perfil': true, 'ativa': true }")
    Optional<Imagem> findActivePerfilByPessoa(Pessoa pessoa);

    void deleteByPublicId(String publicId);
}