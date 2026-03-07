package com.example.Tinder_ufs.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageCompressionUtil {

    @Value("${file.compress.width}")
    private int targetWidth;

    @Value("${file.compress.height}")
    private int targetHeight;

    @Value("${file.compress.quality}")
    private double quality;

    /**
     * Comprime a imagem e retorna como array de bytes
     */
    public byte[] compressImage(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(file.getInputStream())
                .size(targetWidth, targetHeight)  // Redimensiona se necessário
                .outputQuality(quality)           // Qualidade da compressão (0.0 - 1.0)
                .outputFormat(getFormat(file.getOriginalFilename())) // Formato de saída
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * Comprime mantendo a proporção original
     */
    public byte[] compressImageMaintainAspectRatio(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(file.getInputStream())
                .scale(1.0)                        // Mantém o tamanho original
                .outputQuality(quality)             // Aplica compressão
                .outputFormat(getFormat(file.getOriginalFilename()))
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * Extrai o formato do arquivo baseado na extensão
     */
    private String getFormat(String filename) {
        if (filename == null) return "jpg";

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        // Suporta formatos comuns
        if (extension.equals("png") || extension.equals("gif") || extension.equals("bmp")) {
            return extension;
        }

        return "jpg"; // padrão
    }
}