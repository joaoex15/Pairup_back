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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemService {

    private final ImagemRepository imagemRepository;
    private final PessoaRepository pessoaRepository;
    private final ImageCompressionUtil imageCompressionUtil;

    @Value("${STORAGE_PATH:/storage}")
    private String storagePath;

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

        Path filePath = Paths.get(storagePath, key);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, imagemComprimida);
        log.info("Imagem gravada no volume: {}", filePath);

        if (isPerfil) {
            imagemRepository.findByPessoaIdAndPerfilTrue(pessoaId)
                    .ifPresent(imagemExistente -> {
                        imagemExistente.setPerfil(false);
                        imagemRepository.save(imagemExistente);
                        log.info("Removida flag de perfil da imagem: {}", imagemExistente.getId());
                    });
        }

        String folderPath = key.substring(0, key.lastIndexOf('/'));
        String url = "/api/imagens/proxy/" + key;

        Imagem imagem = new Imagem(
                pessoa,
                url,
                key,
                folderPath,
                isPerfil,
                (long) imagemComprimida.length,
                file.getContentType()
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
            Path filePath = Paths.get(storagePath, imagem.getPublicId());
            Files.deleteIfExists(filePath);
            log.info("Arquivo removido do volume: {}", filePath);
        } catch (IOException e) {
            log.error("Erro ao remover arquivo do volume: {}", e.getMessage());
        }

        imagem.setAtiva(false);
        imagemRepository.save(imagem);
        log.info("Imagem {} marcada como inativa no banco", imagemId);
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
