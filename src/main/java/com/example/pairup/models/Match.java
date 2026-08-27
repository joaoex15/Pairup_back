package com.example.pairup.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "match")
@Data
public class Match {

    @Id
    private String id;

    private String pessoaId1;

    private String pessoaId2;

    private boolean ativo = true;
}