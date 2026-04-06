package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImagemRepository extends MongoRepository<Imagem, String> {

    List<Imagem> findByPessoaAndAtivaTrue(Pessoa pessoa);

    Optional<Imagem> findByPessoaAndPerfilTrue(Pessoa pessoa);

    Optional<Imagem> findByPublicId(String publicId);

    long countByPessoaAndAtivaTrue(Pessoa pessoa);

    @Query("{ 'ativa': true, 'perfil': false, 'pessoa': ?0 }")
    List<Imagem> findActiveImagesByPessoa(Pessoa pessoa);

    /**
     * ✅ NOVO: busca imagens de múltiplas pessoas em uma única query.
     *    Necessário para evitar N+1 em getAllPerfisWithFilters do PessoaService.
     */
    @Query("{ 'pessoa.$id': { $in: ?0 }, 'ativa': true }")
    List<Imagem> findByPessoaIdInAndAtivaTrue(List<String> pessoaIds);
}