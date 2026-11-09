package com.example.pairup.pessoa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PessoaRepository extends MongoRepository<Pessoa, String> {

    /** Só perfis com cadastro completo vão ao feed: o login Google cria um perfil mínimo. */
    String LISTAVEL = "ativo: true, cienciaResponsabilidade: true, dataNasc: {$ne: null}, "
            + "nome: {$nin: [null, '']}, curso: {$nin: [null, '']}";

    @Query("{" + LISTAVEL + ", interesse: {$ne: null}, genero: {$ne: null}}")
    Page<Pessoa> findListaveis(Pageable pageable);

    @Query("{" + LISTAVEL + ", interesse: ?0, genero: {$ne: null}}")
    Page<Pessoa> findListaveisPorInteresse(Interesse interesse, Pageable pageable);

    @Query("{" + LISTAVEL + ", interesse: {$ne: null}, genero: ?0}")
    Page<Pessoa> findListaveisPorGenero(Genero genero, Pageable pageable);

    @Query("{" + LISTAVEL + ", interesse: ?0, genero: ?1}")
    Page<Pessoa> findListaveisPorInteresseEGenero(Interesse interesse, Genero genero, Pageable pageable);

    Optional<Pessoa> findByUsuarioId(String usuarioId);

    Optional<Pessoa> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsuarioId(String usuarioId);
}
