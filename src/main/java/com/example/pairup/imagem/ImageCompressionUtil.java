package com.example.pairup.imagem;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Valida e normaliza a foto enviada.
 *
 * Segurança:
 *  - O tipo vem dos bytes do arquivo (magic number), nunca do Content-Type nem da extensão
 *    enviados pelo cliente. Antes um HTML/SVG com Content-Type "image/svg+xml" ia direto ao
 *    bucket público (XSS armazenado).
 *  - JPEG e PNG são decodificados e re-codificados: o que sai é só pixel, sem metadados
 *    (EXIF com localização GPS, por exemplo) nem conteúdo extra embutido.
 *  - Dimensões checadas antes de decodificar, contra "decompression bomb" (PNG pequeno que
 *    vira gigabytes na memória), e no máximo {@value #MAX_DECODIFICACOES} decodificações ao mesmo tempo.
 */
@Component
public class ImageCompressionUtil {

    private static final int MAX_DECODIFICACOES = 2;
    private static final int MAX_LADO = 8000;
    private static final long MAX_PIXELS = 40_000_000L;

    private final Semaphore decodificacoes = new Semaphore(MAX_DECODIFICACOES);

    @Value("${file.compress.width:800}")
    private int targetWidth;

    @Value("${file.compress.height:800}")
    private int targetHeight;

    @Value("${file.compress.quality:0.7}")
    private double quality;

    public enum Formato {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        public final String extensao;
        public final String mimeType;

        Formato(String extensao, String mimeType) {
            this.extensao = extensao;
            this.mimeType = mimeType;
        }
    }

    /** Resultado já pronto para gravar: bytes e tipo determinados pelo servidor. */
    public record ImagemProcessada(byte[] bytes, Formato formato) {}

    /**
     * @throws IllegalArgumentException se o conteúdo não for JPEG, PNG ou WebP válido
     */
    public ImagemProcessada processar(byte[] original) {
        Formato formato = detectarFormato(original);
        if (formato == null) {
            throw new IllegalArgumentException("Formato não permitido. Envie JPEG, PNG ou WebP.");
        }

        // O Java não decodifica WebP sem plugin: guarda os bytes originais, já validados pela
        // assinatura RIFF/WEBP e servidos com Content-Type fixo image/webp.
        if (formato == Formato.WEBP) {
            return new ImagemProcessada(original, formato);
        }

        validarDimensoes(original);

        boolean liberado = false;
        try {
            decodificacoes.acquire();
            liberado = true;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(original))
                    .size(targetWidth, targetHeight)
                    .outputQuality(quality)
                    .outputFormat(formato == Formato.PNG ? "png" : "jpg")
                    .toOutputStream(out);
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) throw new IllegalArgumentException("Imagem inválida ou corrompida.");
            return new ImagemProcessada(bytes, formato);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processamento interrompido", e);
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            // Sem fallback para os bytes originais: se não decodifica, não é imagem válida
            throw new IllegalArgumentException("Imagem inválida ou corrompida.");
        } finally {
            if (liberado) decodificacoes.release();
        }
    }

    static Formato detectarFormato(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Formato.JPEG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Formato.PNG;
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    /** Lê só o cabeçalho (largura/altura), sem decodificar os pixels. */
    private void validarDimensoes(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = iis == null ? null : ImageIO.getImageReaders(iis);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Imagem inválida ou corrompida.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_LADO || h > MAX_LADO || w * h > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "Resolução da imagem muito grande (máximo " + MAX_LADO + "px por lado).");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Imagem inválida ou corrompida.");
        }
    }
}
