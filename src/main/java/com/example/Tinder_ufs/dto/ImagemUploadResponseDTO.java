package com.example.Tinder_ufs.dto;

import com.example.Tinder_ufs.models.Imagem;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadResponseDTO {
    private String id;
    private String url;
    private String nomeOriginal;
    private boolean perfil;
    private long tamanhoBytes;
    private String mensagem;
    private boolean sucesso;

    public ImagemUploadResponseDTO(String id, String url) {
        this.id = id;
        this.url = url;
        this.sucesso = true;
    }

    public static ImagemUploadResponseDTO fromImagem(Imagem imagem, String nomeOriginal) {
        ImagemUploadResponseDTO dto = new ImagemUploadResponseDTO();
        dto.setId(imagem.getId());
        dto.setUrl(imagem.getUrl());
        dto.setNomeOriginal(nomeOriginal);
        dto.setPerfil(imagem.isPerfil());
        dto.setTamanhoBytes(imagem.getTamanhoBytes());
        dto.setSucesso(true);
        dto.setMensagem("Upload realizado com sucesso");
        return dto;
    }

    public static ImagemUploadResponseDTO error(String mensagem) {
        ImagemUploadResponseDTO dto = new ImagemUploadResponseDTO();
        dto.setSucesso(false);
        dto.setMensagem(mensagem);
        return dto;
    }

    public static ImagemUploadResponseDTO success(String mensagem) {
        ImagemUploadResponseDTO dto = new ImagemUploadResponseDTO();
        dto.setSucesso(true);
        dto.setMensagem(mensagem);
        return dto;
    }
}