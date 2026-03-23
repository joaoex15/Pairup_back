package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.config.GoogleDriveConfig;
import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import com.example.Tinder_ufs.utils.ImageCompressionUtil;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ImagemService {

    @Autowired private ImagemRepository imagemRepository;
    @Autowired private PessoaRepository pessoaRepository;
    @Autowired private Drive googleDriveService;
    @Autowired private GoogleDriveConfig googleDriveConfig;
    @Autowired private ImageCompressionUtil imageCompressionUtil;

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ImagemUploadResponseDTO uploadImagem(MultipartFile arquivo, String pessoaId, boolean isPerfil) {
        try {
            Pessoa pessoa = pessoaRepository.findById(pessoaId)
                    .orElseThrow(() -> new RuntimeException("Pessoa não encontrada: " + pessoaId));

            validarArquivo(arquivo);

            byte[] imagemComprimida = imageCompressionUtil.compressImage(arquivo);

            if (isPerfil) {
                imagemRepository.findByPessoaAndPerfilTrue(pessoa)
                        .ifPresent(antiga -> {
                            deletarImagemGoogleDrive(antiga);
                            imagemRepository.delete(antiga);
                        });
            }

            String nomeArquivo = gerarNomeUnico(arquivo.getOriginalFilename());
            String folderKey = (pessoa.getUsuarioId() != null && !pessoa.getUsuarioId().isBlank())
                    ? pessoa.getUsuarioId() : pessoaId;

            String userFolderId = googleDriveConfig.getOrCreateUserFolder(googleDriveService, folderKey);
            String googleDriveFileId = uploadParaGoogleDrive(imagemComprimida, nomeArquivo, arquivo.getContentType(), userFolderId);
            tornarArquivoPublico(googleDriveFileId);

            String googleDriveUrl = "https://drive.google.com/uc?id=" + googleDriveFileId;

            Imagem imagem = new Imagem(pessoa, "google-drive:" + googleDriveFileId, isPerfil);
            Imagem imagemSalva = imagemRepository.save(imagem);

            return new ImagemUploadResponseDTO(
                    imagemSalva.getId(), googleDriveFileId, googleDriveUrl,
                    imagem.getCaminho(), isPerfil, (long) imagemComprimida.length,
                    "Imagem enviada com sucesso!"
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar imagem: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<ImagemUploadResponseDTO> uploadMultiplasImagens(List<MultipartFile> arquivos, String pessoaId) {
        if (arquivos == null || arquivos.isEmpty()) throw new RuntimeException("Nenhum arquivo enviado");
        return arquivos.stream().map(a -> uploadImagem(a, pessoaId, false)).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEFINIR COMO PERFIL  ← novo método
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Imagem definirComoPerfil(String imagemId) {
        // 1. Busca a imagem que vai virar perfil
        Imagem nova = imagemRepository.findById(imagemId)
                .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + imagemId));

        // 2. Remove o status de perfil da imagem atual da pessoa
        imagemRepository.findByPessoaAndPerfilTrue(nova.getPessoa())
                .ifPresent(antiga -> {
                    if (!antiga.getId().equals(imagemId)) {
                        antiga.setPerfil(false);
                        imagemRepository.save(antiga);
                    }
                });

        // 3. Define a nova como perfil
        nova.setPerfil(true);
        return imagemRepository.save(nova);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────────

    public Imagem buscarPorId(String id) {
        return imagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + id));
    }

    public List<Imagem> listarPorPessoa(String pessoaId) {
        return imagemRepository.findByPessoaId(new ObjectId(pessoaId));
    }

    public Imagem buscarImagemPerfil(String pessoaId) {
        return imagemRepository.findPerfilByPessoaId(new ObjectId(pessoaId))
                .orElseThrow(() -> new RuntimeException("Imagem de perfil não encontrada: " + pessoaId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELEÇÃO
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void deletarImagem(String id) {
        Imagem imagem = buscarPorId(id);
        boolean eraPerfil = imagem.isPerfil();
        Pessoa pessoa = imagem.getPessoa();

        deletarImagemGoogleDrive(imagem);
        imagemRepository.delete(imagem);

        // Se era perfil, promove a próxima imagem disponível
        if (eraPerfil) {
            List<Imagem> restantes = imagemRepository.findByPessoaId(new ObjectId(pessoa.getId()));
            if (!restantes.isEmpty()) {
                restantes.get(0).setPerfil(true);
                imagemRepository.save(restantes.get(0));
            }
        }
    }

    @Transactional
    public void deletarTodasPorPessoa(String pessoaId) {
        List<Imagem> imagens = imagemRepository.findByPessoaId(new ObjectId(pessoaId));
        imagens.forEach(this::deletarImagemGoogleDrive);
        imagemRepository.deleteByPessoaId(new ObjectId(pessoaId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GOOGLE DRIVE — internos
    // ─────────────────────────────────────────────────────────────────────────

    private String uploadParaGoogleDrive(byte[] bytes, String nome, String contentType, String parentId) throws IOException {
        File meta = new File();
        meta.setName(nome);
        if (parentId != null && !parentId.isBlank()) {
            meta.setParents(Collections.singletonList(parentId));
        } else if (googleDriveConfig.getRootFolderId() != null && !googleDriveConfig.getRootFolderId().isBlank()) {
            meta.setParents(Collections.singletonList(googleDriveConfig.getRootFolderId()));
        }
        ByteArrayContent media = new ByteArrayContent(contentType != null ? contentType : "image/jpeg", bytes);
        return googleDriveService.files().create(meta, media).setFields("id").execute().getId();
    }

    private void tornarArquivoPublico(String fileId) throws IOException {
        googleDriveService.permissions().create(fileId,
                new Permission().setType("anyone").setRole("reader")).execute();
    }

    private void deletarImagemGoogleDrive(Imagem imagem) {
        try {
            if (imagem.getCaminho() != null && imagem.getCaminho().startsWith("google-drive:")) {
                String fileId = imagem.getCaminho().substring("google-drive:".length());
                googleDriveService.files().delete(fileId).execute();
            }
        } catch (IOException e) {
            System.out.println("[Drive] Falha ao deletar: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITÁRIOS
    // ─────────────────────────────────────────────────────────────────────────

    private String gerarNomeUnico(String nomeOriginal) {
        String ext = ".jpg";
        if (nomeOriginal != null && nomeOriginal.contains("."))
            ext = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        return UUID.randomUUID() + ext;
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty())
            throw new RuntimeException("Arquivo vazio ou não enviado");
        String ct = arquivo.getContentType();
        if (ct == null || !ct.startsWith("image/"))
            throw new RuntimeException("Apenas imagens são permitidas");
        if (arquivo.getSize() > 10L * 1024 * 1024)
            throw new RuntimeException("Imagem muito grande. Máx: 10MB");
    }
}