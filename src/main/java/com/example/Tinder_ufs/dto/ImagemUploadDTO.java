package com.example.Tinder_ufs.dto;

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

    // Validação básica
    public boolean isValid() {
        return file != null && !file.isEmpty();
    }
}