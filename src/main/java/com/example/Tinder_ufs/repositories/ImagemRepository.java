package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImagemRepository extends MongoRepository<Imagem, String> {

    // Buscar todas as imagens de uma pessoa específica
    List<Imagem> findByPessoa(Pessoa pessoa);

    // Buscar todas as imagens de uma pessoa pelo ID da pessoa
    @Query("{ 'pessoa.$id' : ?0 }")
    List<Imagem> findByPessoaId(String pessoaId);

    // Buscar a imagem de perfil de uma pessoa
    Optional<Imagem> findByPessoaAndPerfilTrue(Pessoa pessoa);

    // Buscar a imagem de perfil de uma pessoa pelo ID da pessoa
    @Query("{ 'pessoa.$id' : ?0, 'perfil' : true }")
    Optional<Imagem> findPerfilByPessoaId(String pessoaId);

    // Buscar todas as imagens que são de perfil
    List<Imagem> findByPerfilTrue();

    // Buscar todas as imagens que NÃO são de perfil
    List<Imagem> findByPerfilFalse();

    // Verificar se uma pessoa já tem uma imagem de perfil
    boolean existsByPessoaAndPerfilTrue(Pessoa pessoa);

    // Verificar se uma pessoa já tem uma imagem de perfil pelo ID da pessoa
    @Query(value = "{ 'pessoa.$id' : ?0, 'perfil' : true }", exists = true)
    boolean existsPerfilByPessoaId(String pessoaId);

    // Contar quantas imagens uma pessoa tem
    long countByPessoa(Pessoa pessoa);

    // Deletar todas as imagens de uma pessoa
    void deleteByPessoa(Pessoa pessoa);

    // Deletar todas as imagens de uma pessoa pelo ID da pessoa
    @Query(delete = true, value = "{ 'pessoa.$id' : ?0 }")
    void deleteByPessoaId(String pessoaId);
}