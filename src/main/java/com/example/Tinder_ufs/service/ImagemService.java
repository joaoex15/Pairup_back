package com.example.Tinder_ufs.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ImagemService {

    private final Cloudinary cloudinary;
    private final ImagemRepository imagemRepository;
    private final PessoaRepository pessoaRepository;

    public Imagem salvarImagem(MultipartFile file, String pessoaId, boolean isPerfil) {
        // Busca a pessoa
        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada"));

        // Se for perfil e já existir, remove a flag da antiga
        if (isPerfil) {
            Optional<Imagem> perfilExistente = imagemRepository.findByPessoaAndPerfilTrue(pessoa);
            perfilExistente.ifPresent(img -> {
                img.setPerfil(false);
                imagemRepository.save(img);
            });
        }

        try {
            // Upload para o Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "tinder_ufs_fotos",
                    "transformation", "c_fill,g_face,w_800,h_800"
            ));

            String secureUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            // Cria e salva a imagem
            Imagem imagem = new Imagem(pessoa, secureUrl, publicId, isPerfil);
            return imagemRepository.save(imagem);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload da imagem para o Cloudinary: " + e.getMessage(), e);
        }
    }

    public void deletarImagem(String imagemId, String pessoaId) {
        // Busca a imagem
        Imagem imagem = imagemRepository.findById(imagemId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada"));

        // Verifica se a imagem pertence à pessoa
        if (!imagem.getPessoa().getId().equals(pessoaId)) {
            throw new SecurityException("Você não tem permissão para deletar esta imagem.");
        }

        try {
            // Deleta do Cloudinary
            cloudinary.uploader().destroy(imagem.getPublicId(), ObjectUtils.emptyMap());
            // Deleta do MongoDB
            imagemRepository.delete(imagem);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar imagem do Cloudinary: " + e.getMessage(), e);
        }
    }
}