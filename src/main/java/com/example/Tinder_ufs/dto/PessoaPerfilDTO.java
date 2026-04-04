package com.example.Tinder_ufs.dto;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.models.Tag;
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
    private List<Tag> tags;

    // ==================== CAMPOS ADICIONADOS ====================

    /** Lista de imagens do usuário */
    private List<Imagem> imagens;

    /** URL da foto de perfil */
    private String fotoPerfilUrl;

    // ==================== MÉTODOS AUXILIARES (opcional) ====================

    /**
     * Verifica se o usuário tem foto de perfil
     */
    public boolean hasFotoPerfil() {
        return fotoPerfilUrl != null && !fotoPerfilUrl.isEmpty();
    }

    /**
     * Retorna a quantidade de imagens na galeria (excluindo foto de perfil)
     */
    public int getQuantidadeGaleria() {
        if (imagens == null) return 0;
        if (fotoPerfilUrl == null) return imagens.size();
        return (int) imagens.stream()
                .filter(img -> !img.getUrl().equals(fotoPerfilUrl))
                .count();
    }
}