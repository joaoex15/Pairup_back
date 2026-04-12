package com.example.Tinder_ufs.dto;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
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
    private boolean cienciaResponsabilidade;  // Adicione se não tiver

    // ✅ ADICIONE ESTES CAMPOS (igual ao PessoaCompletaDTO)
    private List<Imagem> imagens;
    private String fotoPerfilUrl;

    // Métodos auxiliares (opcional)
    public boolean hasFotoPerfil() {
        return fotoPerfilUrl != null && !fotoPerfilUrl.isEmpty();
    }
}