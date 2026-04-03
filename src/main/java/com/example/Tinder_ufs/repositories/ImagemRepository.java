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

    // === MÉTODOS EXISTENTES (MANTIDOS) ===

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

    // === ✅ NOVOS MÉTODOS PARA O ImagemService E ImagemProxyController ===

    // Busca imagem por publicId (necessário para o ImagemProxyController)
    Optional<Imagem> findByPublicId(String publicId);

    // Busca todas as imagens de uma pessoa que NÃO são perfil
    @Query("{ 'pessoa.$id' : ?0, 'perfil' : false }")
    List<Imagem> findNonPerfilImagesByPessoaId(String pessoaId);

    // Busca imagem específica verificando se pertence à pessoa (segurança)
    @Query("{ '_id' : ?0, 'pessoa.$id' : ?1 }")
    Optional<Imagem> findByIdAndPessoaId(String imagemId, String pessoaId);

    // Deleta uma imagem específica verificando se pertence à pessoa
    @Query(delete = true, value = "{ '_id' : ?0, 'pessoa.$id' : ?1 }")
    void deleteByIdAndPessoaId(String imagemId, String pessoaId);

    // Conta quantas imagens uma pessoa tem (útil para validação de limite)
    @Query(value = "{ 'pessoa.$id' : ?0 }", count = true)
    long countByPessoaId(String pessoaId);

    // Busca todas as imagens de múltiplas pessoas (útil para feeds)
    @Query(value = "{ 'pessoa.$id' : { $in: ?0 } }")
    List<Imagem> findByPessoaIds(List<String> pessoaIds);
}