package com.example.pairup.imagem;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemUploadDTO {
    private MultipartFile file;
    private boolean perfil;
}