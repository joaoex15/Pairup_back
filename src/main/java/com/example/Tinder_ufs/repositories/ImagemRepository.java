package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImagemRepository extends MongoRepository<Imagem, String> {

    Optional<Imagem> findByPublicId(String publicId);

    List<Imagem> findByPessoaIdAndAtivaTrue(String pessoaId);

    Optional<Imagem> findByPessoaIdAndPerfilTrue(String pessoaId);

    long countByPessoaIdAndAtivaTrue(String pessoaId);

    List<Imagem> findByPessoaAndAtivaTrue(Pessoa pessoa);

    Optional<Imagem> findByPessoaAndPerfilTrue(Pessoa pessoa);

    List<Imagem> findByPessoaIdInAndAtivaTrue(List<String> pessoaIds);

    List<Imagem> findByPessoaId(String pessoaId);
}