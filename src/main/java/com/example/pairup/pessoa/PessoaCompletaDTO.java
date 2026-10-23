package com.example.pairup.pessoa;

import com.example.pairup.imagem.Imagem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * Perfil do próprio usuário (GET /pessoas/me): os dados públicos de {@link PessoaPerfilDTO}
 * mais as redes sociais e os campos calculados abaixo.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PessoaCompletaDTO extends PessoaPerfilDTO {

    // ==================== REDES SOCIAIS ====================
    private String instagram;
    private String whatsapp;
    private String telegram;

    // ==================== MÉTODOS AUXILIARES ====================

    public int getQuantidadeGaleria() {
        List<Imagem> imagens = getImagens();
        if (imagens == null) return 0;
        if (getFotoPerfilUrl() == null) return imagens.size();
        return (int) imagens.stream()
                .filter(img -> !img.getUrl().equals(getFotoPerfilUrl()))
                .count();
    }

    public List<Imagem> getImagensGaleria() {
        List<Imagem> imagens = getImagens();
        if (imagens == null || getFotoPerfilUrl() == null) return imagens;
        return imagens.stream()
                .filter(img -> !img.getUrl().equals(getFotoPerfilUrl()))
                .toList();
    }

    public boolean isPerfilCompleto() {
        return getNome() != null && !getNome().isEmpty()
                && getCurso() != null && !getCurso().isEmpty()
                && getDataNasc() != null
                && getGenero() != null
                && getInteresse() != null
                && isCienciaResponsabilidade();
    }
}
