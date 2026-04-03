package com.example.Tinder_ufs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadDTO {

    /** ID da imagem no MongoDB */
    private String id;

    /** URL pública da imagem no Cloudinary */
    private String url;

    /** ID do arquivo no Google Drive (se aplicável) */
    private String googleDriveId;

    /** URL pública do Google Drive (se aplicável) */
    private String googleDriveUrl;

    /** Caminho interno salvo no MongoDB (formato: "cloudinary:<publicId>") */
    private String caminho;

    /** true se for imagem de perfil */
    private boolean perfil;

    /** Tamanho do arquivo após compressão (em bytes) */
    private long tamanhoBytes;

    /** Mensagem de retorno */
    private String mensagem;
}