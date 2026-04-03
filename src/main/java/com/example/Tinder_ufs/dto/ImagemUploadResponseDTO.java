package com.example.Tinder_ufs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadResponseDTO {

    /** ID da imagem no MongoDB */
    private String id;

    /** URL pública da imagem no Cloudinary */
    private String url;

    /** Nome do arquivo original */
    private String nomeOriginal;

    /** Define se é foto de perfil */
    private boolean perfil;

    /** Tamanho do arquivo em bytes */
    private long tamanhoBytes;

    /** Mensagem de retorno */
    private String mensagem;

    public ImagemUploadResponseDTO(String id, String url) {
        this.id = id;
        this.url = url;
    }
}