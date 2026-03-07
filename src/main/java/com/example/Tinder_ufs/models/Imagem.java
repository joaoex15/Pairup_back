package com.example.Tinder_ufs.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

@Document(collection = "imagem")
@Data
public class Imagem {

    @Id
    private String id;

    @DBRef
    private Pessoa pessoa;

    private String caminho;

    private boolean perfil;

    // Construtor padrão (necessário para o Lombok @Data)
    public Imagem() {
    }

    // Construtor com parâmetros
    public Imagem(Pessoa pessoa, String caminho, boolean perfil) {
        this.pessoa = pessoa;
        this.caminho = caminho;
        this.perfil = perfil;
    }
}