package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.Pessoa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PessoaRepository extends MongoRepository<Pessoa, String> {

    // ==================== MÉTODOS EXISTENTES ====================

    Optional<Pessoa> findByEmail(String email);

    Optional<Pessoa> findByUsuarioId(String usuarioId);

    List<Pessoa> findByAtivoTrue();

    List<Pessoa> findByGenero(String genero);

    // ==================== MÉTODOS ADICIONADOS ====================

    /**
     * Verifica se email já existe (para validação)
     */
    boolean existsByEmail(String email);

    /**
     * Verifica se usuarioId já existe (para validação)
     */
    boolean existsByUsuarioId(String usuarioId);

    /**
     * Busca pessoa por ID ignorando case sensitive
     */
    Optional<Pessoa> findByIdIgnoreCase(String id);

    /**
     * Busca pessoas por interesse
     */
    List<Pessoa> findByInteresse(String interesse);

    /**
     * Busca pessoas ativas por gênero
     */
    List<Pessoa> findByAtivoTrueAndGenero(String genero);

    /**
     * Busca pessoas que aceitaram os termos
     */
    List<Pessoa> findByCienciaResponsabilidadeTrue();

    /**
     * Busca pessoas que NÃO aceitaram os termos
     */
    List<Pessoa> findByCienciaResponsabilidadeFalse();

    /**
     * Busca pessoas por curso
     */
    List<Pessoa> findByCursoContainingIgnoreCase(String curso);

    /**
     * Busca pessoas com tags específicas
     */
    @Query("{ 'tags.nome' : { $in: ?0 } }")
    List<Pessoa> findByTagsNomes(List<String> tagNomes);

    /**
     * Busca pessoas com mais de X anos
     */
    @Query("{ 'dataNasc' : { $lte: ?0 } }")
    List<Pessoa> findByIdadeMenorQue(LocalDate dataLimite);

    /**
     * Conta quantas pessoas ativas
     */
    long countByAtivoTrue();

    /**
     * Conta quantas pessoas aceitaram os termos
     */
    long countByCienciaResponsabilidadeTrue();
}