package com.example.pairup.pessoa;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
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

    @Size(max = 100)
    private String nome;

    @Size(max = 150)
    private String curso;

    @Past
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNasc;

    /** Ignorado: o e-mail do perfil é sempre o da conta (User), que o cliente não escolhe aqui. */
    @Size(max = 254)
    private String email;
    private Genero genero;
    private Interesse interesse;

    @Size(max = 500)
    private String descricao;

    // Só o usuário (com ou sem @): o front monta o link com prefixo fixo, e nada além
    // de letras/números/ponto/sublinhado chega ao href.
    @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "usuário do Instagram inválido")
    private String instagram;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{8,20}$", message = "número de WhatsApp inválido")
    private String whatsapp;

    @Pattern(regexp = "^$|^@?[A-Za-z0-9_]{1,32}$", message = "usuário do Telegram inválido")
    private String telegram;

    @Size(max = MAX_TAGS, message = "no máximo " + MAX_TAGS + " tags")
    private List<String> tags;

    private boolean cienciaResponsabilidade;
}
