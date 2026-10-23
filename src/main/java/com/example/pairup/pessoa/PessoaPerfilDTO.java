package com.example.pairup.pessoa;

import com.example.pairup.imagem.Imagem;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PessoaPerfilDTO {
    private String id;
    private String nome;
    private String curso;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNasc;

    private String email;
    private Genero genero;
    private Interesse interesse;
    private String descricao;
    private List<String> tags;
    private boolean cienciaResponsabilidade;

    private List<Imagem> imagens;
    private String fotoPerfilUrl;
}