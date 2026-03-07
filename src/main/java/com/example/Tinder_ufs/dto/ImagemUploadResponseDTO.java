package com.example.Tinder_ufs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadResponseDTO {
    private String id;              // ID da imagem no MongoDB
    private String googleDriveId;    // ID do arquivo no Google Drive
    private String googleDriveUrl;   // URL pública da imagem
    private String caminho;          // Caminho salvo no formato "google-drive:fileId"
    private boolean perfil;          // Se é imagem de perfil
    private long tamanhoBytes;       // Tamanho após compressão
    private String mensagem;          // Mensagem de retorno
}