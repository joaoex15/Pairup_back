package com.example.Tinder_ufs.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

@Document(collection = "imagem")
@Data
@NoArgsConstructor
public class Imagem {

    @Id
    private String id;

    /**
     * Referência à pessoa dona desta imagem.
     * Armazenado como DBRef no MongoDB: { "$ref": "pessoa", "$id": "<pessoaId>" }
     */
    @DBRef
    private Pessoa pessoa;

    /**
     * Caminho/referência do arquivo no Google Drive.
     * Formato: "google-drive:<fileId>"
     * Exemplo: "google-drive:1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs"
     */
    private String caminho;

    /**
     * true  → imagem de perfil (apenas uma por pessoa)
     * false → imagem comum da galeria
     */
    private boolean perfil;

    public Imagem(Pessoa pessoa, String caminho, boolean perfil) {
        this.pessoa = pessoa;
        this.caminho = caminho;
        this.perfil = perfil;
    }
}