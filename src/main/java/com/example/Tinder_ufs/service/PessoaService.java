package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.dto.PessoaCompletaDTO;
import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.Tag;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.repositories.ImagemRepository;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final ImagemRepository imagemRepository;

    // ==================== BUSCAS COMPLETAS ====================

    @Transactional(readOnly = true)
    public PessoaCompletaDTO getPessoaCompletaById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) return null;
        return convertToCompletaDTO(pessoa);
    }

    @Transactional(readOnly = true)
    public PessoaCompletaDTO getPessoaCompletaByUsuarioId(String usuarioId) {
        Pessoa pessoa = findByUsuarioId(usuarioId);
        if (pessoa == null) return null;
        return convertToCompletaDTO(pessoa);
    }

    // ==================== PERFIL COM IMAGENS ====================

    @Transactional(readOnly = true)
    public PessoaPerfilDTO getPerfilComImagensById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) return null;

        PessoaPerfilDTO dto = convertToPerfilDTO(pessoa);
        List<Imagem> imagens = imagemRepository.findByPessoaAndAtivaTrue(pessoa);
        dto.setImagens(imagens);
        imagens.stream()
                .filter(Imagem::isPerfil)
                .findFirst()
                .ifPresent(perfil -> dto.setFotoPerfilUrl(perfil.getUrl()));

        return dto;
    }

    @Transactional(readOnly = true)
    public List<Imagem> getImagensByPessoaId(String pessoaId) {
        Pessoa pessoa = findById(pessoaId);
        if (pessoa == null) return List.of();
        return imagemRepository.findByPessoaAndAtivaTrue(pessoa);
    }

    @Transactional(readOnly = true)
    public String getFotoPerfilUrl(String pessoaId) {
        Pessoa pessoa = findById(pessoaId);
        if (pessoa == null) return null;
        return imagemRepository.findByPessoaAndPerfilTrue(pessoa)
                .map(Imagem::getUrl)
                .orElse(null);
    }

    // ==================== FILTROS ====================

    @Transactional(readOnly = true)
    public Page<PessoaPerfilDTO> getAllPerfisWithFilters(
            Interesse interesse, Genero genero, Pageable pageable) {

        // 1. Delegar filtros e paginação ao banco
        Page<Pessoa> page;
        if (interesse != null && genero != null) {
            page = pessoaRepository.findByAtivoTrueAndInteresseAndGenero(interesse, genero, pageable);
        } else if (interesse != null) {
            page = pessoaRepository.findByAtivoTrueAndInteresse(interesse, pageable);
        } else if (genero != null) {
            page = pessoaRepository.findByAtivoTrueAndGenero(genero, pageable);
        } else {
            page = pessoaRepository.findByAtivoTrue(pageable);
        }

        if (page.isEmpty()) return page.map(p -> null);

        // 2. Buscar TODAS as imagens da página em uma única query (evita N+1)
        List<String> pessoaIds = page.getContent().stream()
                .map(Pessoa::getId)
                .collect(Collectors.toList());

        List<Imagem> todasImagens = imagemRepository.findByPessoaIdInAndAtivaTrue(pessoaIds);

        Map<String, List<Imagem>> imagensPorPessoa = todasImagens.stream()
                .collect(Collectors.groupingBy(img -> img.getPessoa().getId()));

        // 3. Montar DTOs sem novas queries
        List<PessoaPerfilDTO> dtos = page.getContent().stream().map(pessoa -> {
            PessoaPerfilDTO dto = convertToPerfilDTO(pessoa);
            List<Imagem> imagens = imagensPorPessoa.getOrDefault(pessoa.getId(), List.of());
            dto.setImagens(imagens);
            imagens.stream()
                    .filter(Imagem::isPerfil)
                    .findFirst()
                    .ifPresent(p -> dto.setFotoPerfilUrl(p.getUrl()));
            return dto;
        }).collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    // ==================== CRUD COM VALIDAÇÃO ====================

    @Transactional
    public Pessoa create(Pessoa pessoa) {
        if (!pessoa.isCienciaResponsabilidade()) {
            log.warn("Tentativa de criar usuário sem aceitar os termos: {}", pessoa.getEmail());
            throw new RuntimeException("É necessário aceitar os termos de responsabilidade para criar uma conta");
        }

        if (!pessoa.isMaiorDeIdade()) {
            log.warn("Tentativa de criar usuário menor de idade: {}", pessoa.getEmail());
            throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
        }

        if (pessoaRepository.existsByEmail(pessoa.getEmail())) {
            throw new RuntimeException("Email já cadastrado");
        }

        if (pessoa.getUsuarioId() != null && pessoaRepository.existsByUsuarioId(pessoa.getUsuarioId())) {
            throw new RuntimeException("Usuário já possui cadastro");
        }

        log.info("Criando novo usuário: {} - Aceitou termos: {}", pessoa.getEmail(), pessoa.isCienciaResponsabilidade());
        return pessoaRepository.save(pessoa);
    }

    @Transactional
    public Pessoa update(Pessoa pessoa) {
        Pessoa existing = findById(pessoa.getId());

        if (existing != null) {
            if (existing.isCienciaResponsabilidade() && !pessoa.isCienciaResponsabilidade()) {
                log.warn("Tentativa de desmarcar termos de responsabilidade: {}", existing.getEmail());
                throw new RuntimeException("Não é possível desmarcar os termos de responsabilidade");
            }

            if (pessoa.getDataNasc() != null && !pessoa.getDataNasc().equals(existing.getDataNasc())) {
                if (!pessoa.isMaiorDeIdade()) {
                    throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
                }
            }

            BeanUtils.copyProperties(pessoa, existing, "id", "usuarioId", "cienciaResponsabilidade");
            log.info("Atualizando usuário: {}", existing.getEmail());
            return pessoaRepository.save(existing);
        }
        return null;
    }

    @Transactional
    public Pessoa marcarCienciaResponsabilidade(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) throw new RuntimeException("Pessoa não encontrada");
        pessoa.setCienciaResponsabilidade(true);
        log.info("Usuário {} aceitou os termos de responsabilidade", pessoa.getEmail());
        return pessoaRepository.save(pessoa);
    }

    // ==================== MÉTODOS BÁSICOS ====================

    public PessoaPerfilDTO getPerfilById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) return null;
        return convertToPerfilDTO(pessoa);
    }

    public PessoaRedesSociaisDTO getRedesSociaisById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) return null;
        return convertToRedesSociaisDTO(pessoa);
    }

    /**
     * ✅ Busca pessoa pelo ID
     */
    public Pessoa findById(String id) {
        return pessoaRepository.findById(id).orElse(null);
    }

    /**
     * ✅ Busca pessoa pelo ID do usuário (userId do User)
     */
    public Pessoa findByUsuarioId(String usuarioId) {
        return pessoaRepository.findByUsuarioId(usuarioId).orElse(null);
    }

    public void delete(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa != null) {
            log.info("Deletando usuário: {}", pessoa.getEmail());
            pessoaRepository.deleteById(id);
        }
    }

    // ==================== CONVERSORES ====================

    private PessoaCompletaDTO convertToCompletaDTO(Pessoa pessoa) {
        List<Imagem> imagens = imagemRepository.findByPessoaAndAtivaTrue(pessoa);

        String fotoPerfilUrl = imagens.stream()
                .filter(Imagem::isPerfil)
                .map(Imagem::getUrl)
                .findFirst()
                .orElse(null);

        PessoaCompletaDTO dto = new PessoaCompletaDTO();
        dto.setId(pessoa.getId());
        dto.setNome(pessoa.getNome());
        dto.setCurso(pessoa.getCurso());
        dto.setDataNasc(pessoa.getDataNasc());
        dto.setEmail(pessoa.getEmail());
        dto.setGenero(pessoa.getGenero());
        dto.setInteresse(pessoa.getInteresse());
        dto.setDescricao(pessoa.getDescricao());
        dto.setCienciaResponsabilidade(pessoa.isCienciaResponsabilidade());
        dto.setInstagram(pessoa.getInstagram());
        dto.setWhatsapp(pessoa.getWhatsapp());
        dto.setTelegram(pessoa.getTelegram());
        dto.setImagens(imagens);
        dto.setFotoPerfilUrl(fotoPerfilUrl);

        if (pessoa.getTags() != null) {
            List<String> tagNomes = pessoa.getTags().stream()
                    .map(Tag::getNome)
                    .collect(Collectors.toList());
            dto.setTags(tagNomes);
        } else {
            dto.setTags(new ArrayList<>());
        }

        return dto;
    }

    private PessoaPerfilDTO convertToPerfilDTO(Pessoa pessoa) {
        PessoaPerfilDTO dto = new PessoaPerfilDTO();
        dto.setId(pessoa.getId());
        dto.setNome(pessoa.getNome());
        dto.setCurso(pessoa.getCurso());
        dto.setDataNasc(pessoa.getDataNasc());
        dto.setEmail(pessoa.getEmail());
        dto.setGenero(pessoa.getGenero());
        dto.setInteresse(pessoa.getInteresse());
        dto.setDescricao(pessoa.getDescricao());

        if (pessoa.getTags() != null) {
            List<String> tagNomes = pessoa.getTags().stream()
                    .map(Tag::getNome)
                    .collect(Collectors.toList());
            dto.setTags(tagNomes);
        } else {
            dto.setTags(new ArrayList<>());
        }

        return dto;
    }

    private PessoaRedesSociaisDTO convertToRedesSociaisDTO(Pessoa pessoa) {
        PessoaRedesSociaisDTO dto = new PessoaRedesSociaisDTO();
        dto.setId(pessoa.getId());
        dto.setInstagram(pessoa.getInstagram());
        dto.setWhatsapp(pessoa.getWhatsapp());
        dto.setTelegram(pessoa.getTelegram());
        return dto;
    }
}