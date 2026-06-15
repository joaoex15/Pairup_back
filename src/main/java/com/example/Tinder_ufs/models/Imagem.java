package com.example.Tinder_ufs.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Document(collection = "imagem")
@CompoundIndex(name = "pessoa_perfil_idx", def = "{'pessoa': 1, 'perfil': 1}")
@Data
@NoArgsConstructor
public class Imagem {

    @Id
    private String id;

    @DBRef
    private Pessoa pessoa;

    @NotBlank(message = "URL é obrigatória")
    private String url;

    @NotBlank(message = "PublicId é obrigatório")
    @Indexed(unique = true)
    private String publicId; // key S3: tinder_ufs/{pessoaId}/{uuid}.{ext}

    @NotBlank(message = "FolderPath é obrigatório")
    private String folderPath;

    private boolean perfil = false;

    private LocalDateTime dataUpload;

    private long tamanhoBytes;

    private String mimeType;

    private boolean ativa = true;

    public Imagem(Pessoa pessoa, String url, String publicId, String folderPath,
                  boolean perfil, long tamanhoBytes, String mimeType) {
        this.pessoa = pessoa;
        this.url = url;
        this.publicId = publicId;
        this.folderPath = folderPath;
        this.perfil = perfil;
        this.tamanhoBytes = tamanhoBytes;
        this.mimeType = mimeType;
        this.dataUpload = LocalDateTime.now();
        this.ativa = true;
    }
}
