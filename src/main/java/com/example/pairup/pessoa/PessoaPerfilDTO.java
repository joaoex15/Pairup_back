package com.example.pairup.pessoa;

import com.example.pairup.imagem.Imagem;
import lombok.Data;

import java.util.List;

/**
 * Perfil visto por outras pessoas: não inclui e-mail, data de nascimento nem redes sociais.
 */
@Data
public class PessoaPerfilDTO {
    private String id;
    private String nome;
    private String curso;
    private Integer idade;
    private Genero genero;
    private Interesse interesse;
    private String descricao;
    private List<String> tags;
    private boolean cienciaResponsabilidade;

    private List<Imagem> imagens;
    private String fotoPerfilUrl;
}
