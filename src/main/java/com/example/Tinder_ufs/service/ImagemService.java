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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ImagemService {

    @Autowired
    private ImagemRepository imagemRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private Drive googleDriveService;

    @Autowired
    private GoogleDriveConfig googleDriveConfig;

    @Autowired
    private ImageCompressionUtil imageCompressionUtil;

    @Value("${file.temp.dir}")
    private String tempDir;

    /**
     * Upload de imagem com compressão e salvamento no Google Drive
     */
    @Transactional
    public ImagemUploadResponseDTO uploadImagem(MultipartFile arquivo, String pessoaId, boolean isPerfil) {
        try {
            // 1. Buscar pessoa
            Pessoa pessoa = pessoaRepository.findById(pessoaId)
                    .orElseThrow(() -> new RuntimeException("Pessoa não encontrada"));

            // 2. Validar arquivo
            validarArquivo(arquivo);

            // 3. Comprimir imagem
            byte[] imagemComprimida = imageCompressionUtil.compressImage(arquivo);

            // 4. Se for imagem de perfil, remover perfil antigo
            if (isPerfil) {
                imagemRepository.findByPessoaAndPerfilTrue(pessoa)
                        .ifPresent(imagemAntiga -> {
                            deletarImagemGoogleDrive(imagemAntiga);
                            imagemRepository.delete(imagemAntiga);
                        });
            }

            // 5. Gerar nome único para o arquivo
            String nomeArquivo = gerarNomeUnico(arquivo.getOriginalFilename());

            // 6. Upload para Google Drive
            String googleDriveFileId = uploadParaGoogleDrive(imagemComprimida, nomeArquivo, arquivo.getContentType());

            // 7. Tornar o arquivo público (opcional)
            tornarArquivoPublico(googleDriveFileId);

            // 8. Obter URL do arquivo
            String googleDriveUrl = "https://drive.google.com/uc?id=" + googleDriveFileId;

            // 9. Salvar no MongoDB
            Imagem imagem = new Imagem();
            imagem.setPessoa(pessoa);
            imagem.setCaminho("google-drive:" + googleDriveFileId); // Salva referência ao Google Drive
            imagem.setPerfil(isPerfil);

            Imagem imagemSalva = imagemRepository.save(imagem);

            // 10. Retornar resposta
            return new ImagemUploadResponseDTO(
                    imagemSalva.getId(),
                    googleDriveFileId,
                    googleDriveUrl,
                    imagem.getCaminho(),
                    isPerfil,
                    (long) imagemComprimida.length,
                    "Imagem enviada com sucesso!"
            );

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar imagem: " + e.getMessage(), e);
        }
    }

    /**
     * Upload de múltiplas imagens
     */
    @Transactional
    public List<ImagemUploadResponseDTO> uploadMultiplasImagens(List<MultipartFile> arquivos, String pessoaId) {
        return arquivos.stream()
                .map(arquivo -> {
                    try {
                        return uploadImagem(arquivo, pessoaId, false);
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao processar imagem: " + arquivo.getOriginalFilename(), e);
                    }
                })
                .toList();
    }

    /**
     * Upload para Google Drive
     */
    private String uploadParaGoogleDrive(byte[] imagemBytes, String nomeArquivo, String contentType) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(nomeArquivo);

        // Definir pasta no Google Drive (opcional)
        if (googleDriveConfig.getFolderId() != null && !googleDriveConfig.getFolderId().isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(googleDriveConfig.getFolderId()));
        }

        ByteArrayContent mediaContent = new ByteArrayContent(contentType, imagemBytes);

        File uploadedFile = googleDriveService.files()
                .create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute();

        return uploadedFile.getId();
    }

    /**
     * Tornar arquivo público (qualquer pessoa com link pode ver)
     */
    private void tornarArquivoPublico(String fileId) throws IOException {
        Permission permission = new Permission()
                .setType("anyone")
                .setRole("reader");

        googleDriveService.permissions()
                .create(fileId, permission)
                .execute();
    }

    /**
     * Deletar imagem do Google Drive
     */
    private void deletarImagemGoogleDrive(Imagem imagem) {
        try {
            if (imagem.getCaminho() != null && imagem.getCaminho().startsWith("google-drive:")) {
                String fileId = imagem.getCaminho().substring("google-drive:".length());
                googleDriveService.files().delete(fileId).execute();
            }
        } catch (IOException e) {
            // Log do erro, mas não impede a deleção do registro
            e.printStackTrace();
        }
    }

    /**
     * Gerar nome único para o arquivo
     */
    private String gerarNomeUnico(String nomeOriginal) {
        String extensao = "";
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extensao;
    }

    /**
     * Validar arquivo
     */
    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RuntimeException("Arquivo vazio");
        }

        String contentType = arquivo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Apenas imagens são permitidas");
        }

        // Validar tamanho (máx 10MB antes da compressão)
        if (arquivo.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("Imagem muito grande. Tamanho máximo: 10MB");
        }
    }

    /**
     * Buscar imagem por ID
     */
    public Imagem buscarPorId(String id) {
        return imagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imagem não encontrada"));
    }

    /**
     * Listar imagens de uma pessoa
     */
    public List<Imagem> listarPorPessoa(String pessoaId) {
        return imagemRepository.findByPessoaId(pessoaId);
    }

    /**
     * Buscar imagem de perfil
     */
    public Imagem buscarImagemPerfil(String pessoaId) {
        return imagemRepository.findPerfilByPessoaId(pessoaId)
                .orElseThrow(() -> new RuntimeException("Imagem de perfil não encontrada"));
    }

    /**
     * Deletar imagem
     */
    @Transactional
    public void deletarImagem(String id) {
        Imagem imagem = buscarPorId(id);
        deletarImagemGoogleDrive(imagem);
        imagemRepository.delete(imagem);
    }

    /**
     * Deletar todas as imagens de uma pessoa
     */
    @Transactional
    public void deletarTodasPorPessoa(String pessoaId) {
        List<Imagem> imagens = imagemRepository.findByPessoaId(pessoaId);
        imagens.forEach(this::deletarImagemGoogleDrive);
        imagemRepository.deleteByPessoaId(pessoaId);
    }
}