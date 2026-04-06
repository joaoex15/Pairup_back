package com.example.Tinder_ufs.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemService {

    private final Cloudinary cloudinary;
    private final ImagemRepository imagemRepository;
    private final PessoaRepository pessoaRepository;

    @Transactional
    public Imagem salvarImagem(MultipartFile file, String pessoaId, boolean isPerfil) throws IOException {
        log.info("Salvando imagem para pessoaId: {}, isPerfil: {}", pessoaId, isPerfil);

        // 1. Validações
        validarArquivo(file);

        // 2. Buscar pessoa
        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada com ID: " + pessoaId));

        // 3. Upload para Cloudinary
        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", "tinder_ufs/" + pessoaId,
                "use_filename", true,
                "unique_filename", true,
                "overwrite", false
        );

        Map<?, ?> uploadResult;
        try {
            uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            log.debug("Upload result: {}", uploadResult);
        } catch (Exception e) {
            log.error("Erro no upload para Cloudinary: {}", e.getMessage());
            throw new IOException("Erro ao fazer upload da imagem: " + e.getMessage(), e);
        }

        // 4. Extrair dados - ✅ CORRIGIDO: tratar Integer e Long
        String publicId = (String) uploadResult.get("public_id");
        String url = (String) uploadResult.get("secure_url");

        // ✅ CORREÇÃO: Cloudinary pode retornar Integer ou Long
        Object bytesObj = uploadResult.get("bytes");
        long tamanhoBytes;
        if (bytesObj instanceof Integer) {
            tamanhoBytes = ((Integer) bytesObj).longValue();
        } else if (bytesObj instanceof Long) {
            tamanhoBytes = (Long) bytesObj;
        } else {
            tamanhoBytes = file.getSize(); // fallback
        }

        String folderPath = publicId.contains("/") ? publicId.substring(0, publicId.lastIndexOf('/')) : "";
        String mimeType = file.getContentType();

        log.info("Upload realizado. PublicId: {}, URL: {}, Tamanho: {} bytes", publicId, url, tamanhoBytes);

        // 5. Se for foto de perfil, remover flag de outras fotos
        if (isPerfil) {
            imagemRepository.findByPessoaIdAndPerfilTrue(pessoaId)
                    .ifPresent(imagemExistente -> {
                        imagemExistente.setPerfil(false);
                        imagemRepository.save(imagemExistente);
                        log.info("Removida flag de perfil da imagem: {}", imagemExistente.getId());
                    });
        }

        // 6. Criar e salvar imagem
        Imagem imagem = new Imagem(
                pessoa,
                url,
                publicId,
                folderPath,
                isPerfil,
                tamanhoBytes,
                mimeType
        );
        imagem.setId(UUID.randomUUID().toString());
        imagem.setDataUpload(LocalDateTime.now());
        imagem.setAtiva(true);

        Imagem saved = imagemRepository.save(imagem);
        log.info("Imagem salva no banco com ID: {}", saved.getId());

        return saved;
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
            cloudinary.uploader().destroy(imagem.getPublicId(), ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.error("Erro ao deletar imagem do Cloudinary: {}", e.getMessage());
        }

        imagem.setAtiva(false);
        imagemRepository.save(imagem);
    }

    @Transactional(readOnly = true)
    public Imagem findByPublicId(String publicId) {
        return imagemRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada: " + publicId));
    }

    @Transactional(readOnly = true)
    public List<Imagem> listarImagensAtivasPorUsuario(String pessoaId) {
        return imagemRepository.findByPessoaIdAndAtivaTrue(pessoaId);
    }
}