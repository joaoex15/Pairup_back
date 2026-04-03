package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImagemRepository extends MongoRepository<Imagem, String> {

    // Busca todas as imagens de uma Pessoa (por objeto)
    List<Imagem> findByPessoa(Pessoa pessoa);

    // Busca todas as imagens de uma Pessoa pelo ID
    @Query("{ 'pessoa.$id' : ?0 }")
    List<Imagem> findByPessoaId(String pessoaId);

    // Busca a imagem de perfil de uma Pessoa (por objeto)
    Optional<Imagem> findByPessoaAndPerfilTrue(Pessoa pessoa);

    // Busca a imagem de perfil de uma Pessoa pelo ID
    @Query("{ 'pessoa.$id' : ?0, 'perfil' : true }")
    Optional<Imagem> findPerfilByPessoaId(String pessoaId);

    // Retorna todas as imagens marcadas como perfil
    List<Imagem> findByPerfilTrue();

    // Verifica se uma pessoa já possui imagem de perfil (por objeto)
    boolean existsByPessoaAndPerfilTrue(Pessoa pessoa);

    // Verifica se uma pessoa já possui imagem de perfil (por ID)
    @Query(value = "{ 'pessoa.$id' : ?0, 'perfil' : true }", exists = true)
    boolean existsPerfilByPessoaId(String pessoaId);

    // Conta quantas imagens uma Pessoa possui
    long countByPessoa(Pessoa pessoa);

    // Deleta todas as imagens de uma Pessoa pelo ID
    @Query(delete = true, value = "{ 'pessoa.$id' : ?0 }")
    void deleteByPessoaId(String pessoaId);
}