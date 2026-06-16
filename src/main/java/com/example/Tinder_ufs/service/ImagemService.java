package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import com.example.Tinder_ufs.utils.ImageCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemService {

    private final ImagemRepository imagemRepository;
    private final PessoaRepository pessoaRepository;
    private final ImageCompressionUtil imageCompressionUtil;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.public-url:}")
    private String publicUrl;

    @Transactional
    public Imagem salvarImagem(MultipartFile file, String pessoaId, boolean isPerfil) throws IOException {
        log.info("Salvando imagem para pessoaId: {}, isPerfil: {}", pessoaId, isPerfil);

        validarArquivo(file);

        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada com ID: " + pessoaId));

        byte[] imagemComprimida;
        try {
            imagemComprimida = imageCompressionUtil.compressImage(file);
            log.debug("Imagem comprimida: {} bytes → {} bytes", file.getSize(), imagemComprimida.length);
        } catch (Exception e) {
            log.warn("Falha na compressão, usando bytes originais: {}", e.getMessage());
            imagemComprimida = file.getBytes();
        }

        String extensao = getExtensao(file.getOriginalFilename());
        String key = "tinder_ufs/" + pessoaId + "/" + UUID.randomUUID() + "." + extensao;
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) imagemComprimida.length)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(imagemComprimida));
        log.info("Imagem enviada para R2: {}", key);

        if (isPerfil) {
            imagemRepository.findByPessoaIdAndPerfilTrue(pessoaId)
                    .ifPresent(imagemExistente -> {
                        imagemExistente.setPerfil(false);
                        imagemRepository.save(imagemExistente);
                        log.info("Removida flag de perfil da imagem: {}", imagemExistente.getId());
                    });
        }

        String folderPath = key.substring(0, key.lastIndexOf('/'));
        String url = resolverUrl(key);

        Imagem imagem = new Imagem(
                pessoa,
                url,
                key,
                folderPath,
                isPerfil,
                (long) imagemComprimida.length,
                contentType
        );
        imagem.setId(UUID.randomUUID().toString());

        Imagem saved = imagemRepository.save(imagem);
        log.info("Imagem salva no banco com ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public void deletarImagem(String imagemId, String pessoaId) {
        log.info("Deletando imagem: {} para pessoaId: {}", imagemId, pessoaId);

        Imagem imagem = imagemRepository.findById(imagemId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada: " + imagemId));

        if (!imagem.getPessoa().getId().equals(pessoaId)) {
            throw new SecurityException("Acesso negado: você não é o proprietário desta imagem");
        }

        long countAtivas = imagemRepository.countByPessoaIdAndAtivaTrue(pessoaId);
        if (countAtivas <= 1 && imagem.isAtiva()) {
            throw new IllegalStateException("Não é possível deletar a última imagem do perfil");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(imagem.getPublicId())
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Arquivo removido do R2: {}", imagem.getPublicId());
        } catch (Exception e) {
            log.error("Erro ao remover arquivo do R2: {}", e.getMessage());
        }

        imagem.setAtiva(false);
        imagemRepository.save(imagem);
        log.info("Imagem {} marcada como inativa no banco", imagemId);
    }

    public String gerarUrlPresignada(String publicId) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(req -> req.bucket(bucket).key(publicId))
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    @Transactional(readOnly = true)
    public Imagem findByPublicId(String publicId) {
        return imagemRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada: " + publicId));
    }

    @Transactional(readOnly = true)
    public Imagem findById(String id) {
        return imagemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Imagem> listarImagensAtivasPorUsuario(String pessoaId) {
        return imagemRepository.findByPessoaIdAndAtivaTrue(pessoaId);
    }

    @Transactional
    public Imagem definirFotoPerfil(String imagemId, String pessoaId) {
        log.info("Definindo imagem {} como foto de perfil para pessoa: {}", imagemId, pessoaId);

        Imagem imagem = findById(imagemId);

        if (!imagem.getPessoa().getId().equals(pessoaId)) {
            throw new SecurityException("Acesso negado: você não é o proprietário desta imagem");
        }

        imagemRepository.findByPessoaIdAndPerfilTrue(pessoaId)
                .ifPresent(perfilAtual -> {
                    perfilAtual.setPerfil(false);
                    imagemRepository.save(perfilAtual);
                });

        imagem.setPerfil(true);
        imagemRepository.save(imagem);

        log.info("Foto de perfil atualizada para imagem: {}", imagemId);
        return imagem;
    }

    private String resolverUrl(String key) {
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl + "/" + key;
        }
        return "/api/imagens/proxy/" + key;
    }

    private void validarArquivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode ser vazio");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Envie apenas imagens.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Arquivo excede o limite de 5MB");
        }
    }

    private String getExtensao(String nomeOriginal) {
        if (nomeOriginal == null || !nomeOriginal.contains(".")) return "jpg";
        return nomeOriginal.substring(nomeOriginal.lastIndexOf('.') + 1).toLowerCase();
    }
}
