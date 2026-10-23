package com.example.pairup.pessoa;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Corpo de POST /pessoas e PUT /pessoas/me.
 *
 * Separado de {@link PessoaPerfilDTO} (resposta pública) para poder receber as redes
 * sociais sem expô-las em GET /pessoas — elas só saem em GET /pessoas/me e
 * GET /matches/{matchId}/redes-sociais.
 *
 * tags: IDs das tags (GET /tags/ativas). Na resposta, PessoaPerfilDTO.tags traz os nomes.
 */
@Data
public class PessoaRequestDTO {

    public static final int MAX_TAGS = 5;

    private String nome;
    private String curso;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNasc;

    private String email;
    private Genero genero;
    private Interesse interesse;

    @Size(max = 500)
    private String descricao;

    @Size(max = 100)
    private String instagram;

    @Size(max = 30)
    private String whatsapp;

    @Size(max = 100)
    private String telegram;

    @Size(max = MAX_TAGS, message = "no máximo " + MAX_TAGS + " tags")
    private List<String> tags;

    private boolean cienciaResponsabilidade;
}
