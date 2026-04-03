package com.example.Tinder_ufs.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "imagem")
@Data
@NoArgsConstructor
public class Imagem {

    @Id
    private String id;

    @DBRef
    private Pessoa pessoa;

    private String url;
    private String publicId;
    private boolean perfil = false;

    public Imagem(Pessoa pessoa, String url, String publicId, boolean perfil) {
        this.pessoa = pessoa;
        this.url = url;
        this.publicId = publicId;
        this.perfil = perfil;
    }
}