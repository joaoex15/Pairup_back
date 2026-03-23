package com.example.Tinder_ufs.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ImagemUploadDTO {
    private String pessoaId;
    private MultipartFile arquivo;
    private boolean perfil;
}