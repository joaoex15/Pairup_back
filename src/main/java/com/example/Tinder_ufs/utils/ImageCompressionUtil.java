package com.example.Tinder_ufs.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageCompressionUtil {

    /** Largura máxima após redimensionamento (padrão: 800px) */
    @Value("${file.compress.width:800}")
    private int targetWidth;

    /** Altura máxima após redimensionamento (padrão: 800px) */
    @Value("${file.compress.height:800}")
    private int targetHeight;

    /** Qualidade da compressão JPEG, de 0.0 a 1.0 (padrão: 0.7 = 70%) */
    @Value("${file.compress.quality:0.7}")
    private double quality;

    /**
     * Comprime e redimensiona a imagem recebida.
     * Mantém proporção original (fit dentro de targetWidth x targetHeight).
     *
     * @param file arquivo recebido via multipart
     * @return bytes da imagem comprimida
     */
    public byte[] compressImage(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(file.getInputStream())
                .size(targetWidth, targetHeight)
                .outputQuality(quality)
                .outputFormat(getFormat(file.getOriginalFilename()))
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * Retorna o formato de saída com base na extensão do arquivo original.
     * PNG e GIF mantêm seu formato; todo o resto é convertido para JPG.
     */
    private String getFormat(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (ext) {
            case "png", "gif" -> ext;
            default -> "jpg";
        };
    }
}