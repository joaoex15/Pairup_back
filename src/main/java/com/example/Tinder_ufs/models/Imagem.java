package com.example.Tinder_ufs.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
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

    @NotBlank
    @Pattern(regexp = "^https://res\\.cloudinary\\.com/.*", message = "URL inválida do Cloudinary")
    private String url;

    @NotBlank
    @Indexed(unique = true)
    private String publicId;

    @NotBlank
    private String folderPath; // Pasta do usuário (ex: "tinder_ufs_fotos/usuario_123")

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