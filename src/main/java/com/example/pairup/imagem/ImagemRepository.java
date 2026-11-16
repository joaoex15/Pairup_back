package com.example.pairup.imagem;

import com.example.pairup.pessoa.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImagemRepository extends MongoRepository<Imagem, String> {

    Optional<Imagem> findByPublicId(String publicId);

    List<Imagem> findByPessoaIdAndAtivaTrue(String pessoaId);

    // Lista: registros antigos podem ter uma imagem inativa ainda marcada como perfil
    List<Imagem> findByPessoaIdAndPerfilTrue(String pessoaId);

    boolean existsByPessoaIdAndPerfilTrueAndAtivaTrue(String pessoaId);

    long countByPessoaIdAndAtivaTrue(String pessoaId);

    List<Imagem> findByPessoaAndAtivaTrue(Pessoa pessoa);

    /** Todas as imagens da pessoa, inclusive as inativas (exclusão de perfil). */
    List<Imagem> findByPessoa(Pessoa pessoa);

    // Recebe Pessoa, não String: Imagem.pessoa é @DBRef com ObjectId, e um $in por ids em
    // String nunca casa (o feed ficava sem nenhuma foto).
    List<Imagem> findByPessoaInAndAtivaTrue(List<Pessoa> pessoas);
}