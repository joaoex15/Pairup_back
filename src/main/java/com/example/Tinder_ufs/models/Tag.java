package com.example.Tinder_ufs.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tag")
@Data
public class Tag {
    @Id
    private String id;
    @NotBlank
    private String nome;
    private String descricao;
    @NotBlank
    private String categoria;
    private boolean ativa = true;
}