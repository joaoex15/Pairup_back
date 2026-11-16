package com.example.pairup.match;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Registro de "passar": a pessoa de origem não quer mais ver a de destino no deck. */
@Document(collection = "dislike")
@Data
@CompoundIndex(name = "unique_dislike", def = "{'pessoaOrigemId': 1, 'pessoaDestinoId': 1}", unique = true)
public class Dislike {

    @Id
    private String id;

    private String pessoaOrigemId;

    private String pessoaDestinoId;

    private Instant data = Instant.now();
}
