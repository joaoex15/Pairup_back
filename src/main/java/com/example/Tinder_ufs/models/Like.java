package com.example.Tinder_ufs.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document(collection = "like")
@Data
@CompoundIndexes({
        @CompoundIndex(name = "unique_like", def = "{'pessoaOrigemId': 1, 'pessoaDestinoId': 1}", unique = true)
})
public class Like {

    @Id
    private String id;

    private String pessoaOrigemId;

    private String pessoaDestinoId;

    private boolean ativo = true;
}