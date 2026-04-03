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
        Pessoa pessoa = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada"));

        if (isPerfil) {
            Optional<Imagem> perfilExistente = imagemRepository.findByPessoaAndPerfilTrue(pessoa);
            perfilExistente.ifPresent(img -> {
                img.setPerfil(false);
                imagemRepository.save(img);
            });
        }

        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "tinder_ufs_fotos",
                    "transformation", "c_fill,g_face,w_800,h_800"
            ));

            String secureUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            Imagem imagem = new Imagem(pessoa, secureUrl, publicId, isPerfil);
            return imagemRepository.save(imagem);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload da imagem para o Cloudinary: " + e.getMessage(), e);
        }
    }

    public void deletarImagem(String imagemId, String pessoaId) {
        Imagem imagem = imagemRepository.findById(imagemId)
                .orElseThrow(() -> new IllegalArgumentException("Imagem não encontrada"));

        if (!imagem.getPessoa().getId().equals(pessoaId)) {
            throw new SecurityException("Você não tem permissão para deletar esta imagem.");
        }

        try {
            cloudinary.uploader().destroy(imagem.getPublicId(), ObjectUtils.emptyMap());
            imagemRepository.delete(imagem);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar imagem do Cloudinary: " + e.getMessage(), e);
        }
    }

    // ✅ Método adicionado para o ImagemProxyController
    public Imagem findByPublicId(String publicId) {
        return imagemRepository.findByPublicId(publicId).orElse(null);
    }
}