package com.example.Tinder_ufs.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import com.example.Tinder_ufs.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemService {

    private final Cloudinary cloudinary;
    private final ImagemRepository imagemRepository;
    private final PessoaRepository pessoaRepository;
    private final AuditLogService auditLogService;

    @Value("${app.max-images-per-user:10}")
    private int maxImagesPerUser;

    @Value("${app.max-file-size-mb:5}")
    private int maxFileSizeMB;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final String FOLDER_BASE = "tinder_ufs_fotos";

    @Transactional
    public Imagem salvarImagem(MultipartFile file, String pessoaId, boolean isPerfil) {
        // 1. Validações de segurança
        validateImageUpload(file, pessoaId, isPerfil);

        // 2. Buscar pessoa
        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new SecurityException("Pessoa não encontrada"));

        // 3. Verificar limite de imagens
        long imageCount = imagemRepository.countByPessoaAndAtivaTrue(pessoa);
        if (imageCount >= maxImagesPerUser) {
            throw new IllegalStateException("Limite máximo de " + maxImagesPerUser + " imagens atingido");
        }

        // 4. Se for perfil, desativar foto de perfil anterior
        if (isPerfil) {
            imagemRepository.findByPessoaAndPerfilTrue(pessoa)
                    .ifPresent(perfilAntigo -> {
                        perfilAntigo.setPerfil(false);
                        imagemRepository.save(perfilAntigo);
                        log.info("Foto de perfil anterior desativada: {}", perfilAntigo.getId());
                    });
        }

        // 5. Criar pasta única para o usuário
        String userFolder = String.format("%s/usuario_%s", FOLDER_BASE, pessoa.getId());

        // 6. Gerar nome único com timestamp e hash
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomHash = generateSecureHash();
        String fileName = String.format("img_%s_%s", timestamp, randomHash);
        String fullPublicId = String.format("%s/%s", userFolder, fileName);

        try {
            // 7. Validar tamanho do arquivo
            if (file.getSize() > maxFileSizeMB * 1024 * 1024) {
                throw new IllegalArgumentException("Arquivo excede " + maxFileSizeMB + "MB");
            }

            // 8. Validar MIME type
            String mimeType = file.getContentType();
            if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                throw new IllegalArgumentException("Tipo de arquivo não permitido");
            }

            // 9. Upload para Cloudinary com transformações seguras
            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "public_id", fullPublicId,
                    "folder", userFolder,
                    "transformation", new Object[][]{
                            {"crop", "fill"},
                            {"gravity", "face"},
                            {"width", 800},
                            {"height", 800},
                            {"quality", "auto:good"},
                            {"fetch_format", "auto"}
                    },
                    "allowed_formats", new String[]{"jpg", "jpeg", "png", "webp", "gif"},
                    "type", "upload",
                    "overwrite", false,
                    "unique_filename", false
            );

            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            String secureUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();
            long tamanhoBytes = (long) uploadResult.getOrDefault("bytes", 0L);

            // 10. Salvar no MongoDB
            Imagem imagem = new Imagem(
                    pessoa, secureUrl, publicId, userFolder,
                    isPerfil, tamanhoBytes, mimeType
            );

            Imagem savedImagem = imagemRepository.save(imagem);

            // 11. Log de auditoria
            auditLogService.logImageUpload(pessoa.getId(), savedImagem.getId(), isPerfil);

            log.info("Imagem salva com sucesso - ID: {}, Usuário: {}, Perfil: {}",
                    savedImagem.getId(), pessoa.getId(), isPerfil);

            return savedImagem;

        } catch (IOException e) {
            log.error("Erro no upload da imagem para usuário {}: {}", pessoaId, e.getMessage());
            throw new RuntimeException("Erro ao processar imagem: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deletarImagem(String imagemId, String pessoaId) {
        // 1. Buscar imagem
        Imagem imagem = imagemRepository.findById(imagemId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada"));

        // 2. Verificar propriedade
        if (!imagem.getPessoa().getId().equals(pessoaId)) {
            auditLogService.logSecurityViolation(pessoaId, "Tentativa de deletar imagem de outro usuário");
            throw new SecurityException("Acesso negado: você não é o proprietário desta imagem");
        }

        // 3. Verificar se é a única foto (não pode deletar a última)
        long activeImages = imagemRepository.countByPessoaAndAtivaTrue(imagem.getPessoa());
        if (activeImages <= 1) {
            throw new IllegalStateException("Não é possível deletar a última foto do perfil");
        }

        try {
            // 4. Soft delete primeiro (marca como inativa)
            imagem.setAtiva(false);

            // 5. Se era foto de perfil, promover outra
            if (imagem.isPerfil()) {
                Optional<Imagem> outraImagem = imagemRepository.findActiveImagesByPessoa(imagem.getPessoa())
                        .stream()
                        .filter(img -> !img.getId().equals(imagemId))
                        .findFirst();

                outraImagem.ifPresent(outra -> {
                    outra.setPerfil(true);
                    imagemRepository.save(outra);
                    log.info("Nova foto de perfil promovida: {}", outra.getId());
                });
            }

            imagemRepository.save(imagem);

            // 6. Deletar do Cloudinary (opcional - pode manter backup)
            // cloudinary.uploader().destroy(imagem.getPublicId(), ObjectUtils.emptyMap());

            auditLogService.logImageDeletion(pessoaId, imagemId);
            log.info("Imagem desativada - ID: {}, Usuário: {}", imagemId, pessoaId);

        } catch (Exception e) {
            log.error("Erro ao deletar imagem: {}", e.getMessage());
            throw new RuntimeException("Erro ao deletar imagem do Cloudinary: " + e.getMessage(), e);
        }
    }

    public Imagem findByPublicId(String publicId) {
        return imagemRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada"));
    }

    private void validateImageUpload(MultipartFile file, String pessoaId, boolean isPerfil) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode ser vazio");
        }

        if (pessoaId == null || pessoaId.trim().isEmpty()) {
            throw new IllegalArgumentException("ID da pessoa é obrigatório");
        }

        if (file.getSize() > maxFileSizeMB * 1024 * 1024) {
            throw new IllegalArgumentException(String.format(
                    "Arquivo excede o limite de %dMB (tamanho atual: %.2fMB)",
                    maxFileSizeMB, file.getSize() / (1024.0 * 1024.0)
            ));
        }
    }

    private String generateSecureHash() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] hashBytes = new byte[16];
        secureRandom.nextBytes(hashBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
    }

    public List<Imagem> listarImagensAtivasPorUsuario(String pessoaId) {
        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada"));
        return imagemRepository.findActiveImagesByPessoa(pessoa);
    }
}