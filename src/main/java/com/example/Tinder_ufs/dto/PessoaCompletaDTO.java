package com.example.Tinder_ufs.dto;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PessoaCompletaDTO {

    // ==================== DADOS BÁSICOS ====================
    private String id;
    private String nome;
    private String curso;
    private LocalDate dataNasc;
    private String email;
    private Genero genero;
    private Interesse interesse;
    private String descricao;
    private List<String> tags;
    private boolean cienciaResponsabilidade;

    // ==================== REDES SOCIAIS ====================
    private String instagram;
    private String whatsapp;
    private String telegram;

    // ==================== IMAGENS ====================
    private List<Imagem> imagens;
    private String fotoPerfilUrl;

    // ==================== MÉTODOS AUXILIARES ====================

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

    /**
     * Retorna todas as imagens exceto a foto de perfil
     */
    public List<Imagem> getImagensGaleria() {
        if (imagens == null || fotoPerfilUrl == null) return imagens;
        return imagens.stream()
                .filter(img -> !img.getUrl().equals(fotoPerfilUrl))
                .toList();
    }

    /**
     * Verifica se o perfil está completo
     */
    public boolean isPerfilCompleto() {
        return nome != null && !nome.isEmpty()
                && curso != null && !curso.isEmpty()
                && dataNasc != null
                && genero != null
                && interesse != null
                && cienciaResponsabilidade;
    }
}