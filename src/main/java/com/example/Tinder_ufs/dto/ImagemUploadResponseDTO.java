package com.example.Tinder_ufs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadResponseDTO {

    /** ID do documento salvo no MongoDB */
    private String id;

    /** ID do arquivo no Google Drive */
    private String googleDriveId;

    /** URL pública de acesso direto à imagem */
    private String googleDriveUrl;

    /** Caminho interno salvo no MongoDB (formato: "google-drive:<fileId>") */
    private String caminho;

    /** true se for imagem de perfil */
    private boolean perfil;

    /** Tamanho do arquivo após compressão (em bytes) */
    private long tamanhoBytes;

    /** Mensagem de retorno */
    private String mensagem;
}