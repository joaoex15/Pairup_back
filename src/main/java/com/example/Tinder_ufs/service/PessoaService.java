package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.dto.PessoaCompletaDTO;
import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Imagem;
import com.example.Tinder_ufs.models.Pessoa;
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

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final ImagemRepository imagemRepository;

    // ==================== BUSCAS COMPLETAS ====================

    /**
     * Busca pessoa com TODAS as informações (perfil + redes sociais + imagens)
     */
    @Transactional(readOnly = true)
    public PessoaCompletaDTO getPessoaCompletaById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        return convertToCompletaDTO(pessoa);
    }

    /**
     * Busca pessoa completa por usuarioId (Google ID)
     */
    @Transactional(readOnly = true)
    public PessoaCompletaDTO getPessoaCompletaByUsuarioId(String usuarioId) {
        Pessoa pessoa = findByUsuarioId(usuarioId);
        if (pessoa == null) {
            return null;
        }
        return convertToCompletaDTO(pessoa);
    }

    // ==================== PERFIL COM IMAGENS ====================

    /**
     * Busca perfil com imagens (foto de perfil + galeria)
     */
    @Transactional(readOnly = true)
    public PessoaPerfilDTO getPerfilComImagensById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        PessoaPerfilDTO dto = convertToPerfilDTO(pessoa);

        // Adiciona imagens
        List<Imagem> imagens = imagemRepository.findByPessoaAndAtivaTrue(pessoa);
        dto.setImagens(imagens);

        // Define foto de perfil
        imagens.stream()
                .filter(Imagem::isPerfil)
                .findFirst()
                .ifPresent(perfil -> dto.setFotoPerfilUrl(perfil.getUrl()));

        return dto;
    }

    /**
     * Busca apenas as imagens do usuário
     */
    @Transactional(readOnly = true)
    public List<Imagem> getImagensByPessoaId(String pessoaId) {
        Pessoa pessoa = findById(pessoaId);
        if (pessoa == null) {
            return List.of();
        }
        return imagemRepository.findByPessoaAndAtivaTrue(pessoa);
    }

    /**
     * Busca apenas a foto de perfil do usuário
     */
    @Transactional(readOnly = true)
    public String getFotoPerfilUrl(String pessoaId) {
        Pessoa pessoa = findById(pessoaId);
        if (pessoa == null) {
            return null;
        }
        return imagemRepository.findByPessoaAndPerfilTrue(pessoa)
                .map(Imagem::getUrl)
                .orElse(null);
    }

    // ==================== FILTROS (com imagens) ====================

    /**
     * Busca perfis com filtros e já inclui as imagens
     */
    @Transactional(readOnly = true)
    public Page<PessoaPerfilDTO> getAllPerfisWithFiltersComImagens(Interesse interesse, Genero genero, Pageable pageable) {
        List<Pessoa> pessoas = pessoaRepository.findByAtivoTrue();

        List<PessoaPerfilDTO> filtered = pessoas.stream()
                .filter(p -> {
                    if (interesse == null) return true;
                    if (interesse == Interesse.TODOS) {
                        return p.getInteresse() != Interesse.NAO_DEFINIDO;
                    }
                    return p.getInteresse() == interesse;
                })
                .filter(p -> {
                    if (genero == null) return true;
                    return p.getGenero() == genero;
                })
                .map(pessoa -> {
                    PessoaPerfilDTO dto = convertToPerfilDTO(pessoa);

                    // Carrega imagens
                    List<Imagem> imagens = imagemRepository.findByPessoaAndAtivaTrue(pessoa);
                    dto.setImagens(imagens);

                    // Define foto de perfil
                    imagens.stream()
                            .filter(Imagem::isPerfil)
                            .findFirst()
                            .ifPresent(perfil -> dto.setFotoPerfilUrl(perfil.getUrl()));

                    return dto;
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());

        if (start > filtered.size()) {
            return Page.empty(pageable);
        }

        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    // Versão original (mantida para compatibilidade)
    public Page<PessoaPerfilDTO> getAllPerfisWithFilters(Interesse interesse, Genero genero, Pageable pageable) {
        return getAllPerfisWithFiltersComImagens(interesse, genero, pageable);
    }

    // ==================== CRUD COM VALIDAÇÃO ====================

    /**
     * Cria usuário SOMENTE se cienciaResponsabilidade = TRUE
     */
    @Transactional
    public Pessoa create(Pessoa pessoa) {
        // VALIDAÇÃO OBRIGATÓRIA: só cria se aceitou os termos
        if (!pessoa.isCienciaResponsabilidade()) {
            log.warn("Tentativa de criar usuário sem aceitar os termos: {}", pessoa.getEmail());
            throw new RuntimeException("É necessário aceitar os termos de responsabilidade para criar uma conta");
        }

        // Valida idade
        if (!pessoa.isMaiorDeIdade()) {
            log.warn("Tentativa de criar usuário menor de idade: {}", pessoa.getEmail());
            throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
        }

        // Verifica se email já existe
        if (pessoaRepository.existsByEmail(pessoa.getEmail())) {
            throw new RuntimeException("Email já cadastrado");
        }

        // Verifica se usuário já existe
        if (pessoa.getUsuarioId() != null && pessoaRepository.existsByUsuarioId(pessoa.getUsuarioId())) {
            throw new RuntimeException("Usuário já possui cadastro");
        }

        log.info("Criando novo usuário: {} - Aceitou termos: {}", pessoa.getEmail(), pessoa.isCienciaResponsabilidade());
        return pessoaRepository.save(pessoa);
    }

    /**
     * Atualiza usuário (não pode mudar a ciência para false)
     */
    @Transactional
    public Pessoa update(Pessoa pessoa) {
        Pessoa existing = findById(pessoa.getId());

        if (existing != null) {
            // Não permitir mudar cienciaResponsabilidade para false
            if (existing.isCienciaResponsabilidade() && !pessoa.isCienciaResponsabilidade()) {
                log.warn("Tentativa de desmarcar termos de responsabilidade: {}", existing.getEmail());
                throw new RuntimeException("Não é possível desmarcar os termos de responsabilidade");
            }

            // Valida idade se foi alterada
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

    /**
     * Marca ciência de responsabilidade (método específico)
     */
    @Transactional
    public Pessoa marcarCienciaResponsabilidade(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            throw new RuntimeException("Pessoa não encontrada");
        }
        pessoa.setCienciaResponsabilidade(true);
        log.info("Usuário {} aceitou os termos de responsabilidade", pessoa.getEmail());
        return pessoaRepository.save(pessoa);
    }

    // ==================== MÉTODOS BÁSICOS ====================

    public PessoaPerfilDTO getPerfilById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        return convertToPerfilDTO(pessoa);
    }

    public PessoaRedesSociaisDTO getRedesSociaisById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        return convertToRedesSociaisDTO(pessoa);
    }

    public Pessoa findById(String id) {
        return pessoaRepository.findById(id).orElse(null);
    }

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

    /**
     * Converte para DTO COMPLETO (com todas as informações)
     */
    private PessoaCompletaDTO convertToCompletaDTO(Pessoa pessoa) {
        // Buscar imagens do usuário
        List<Imagem> imagens = imagemRepository.findByPessoaAndAtivaTrue(pessoa);

        // Buscar foto de perfil
        String fotoPerfilUrl = imagens.stream()
                .filter(Imagem::isPerfil)
                .map(Imagem::getUrl)
                .findFirst()
                .orElse(null);

        PessoaCompletaDTO dto = new PessoaCompletaDTO();

        // Dados básicos
        dto.setId(pessoa.getId());
        dto.setNome(pessoa.getNome());
        dto.setCurso(pessoa.getCurso());
        dto.setDataNasc(pessoa.getDataNasc());
        dto.setEmail(pessoa.getEmail());
        dto.setGenero(pessoa.getGenero());
        dto.setInteresse(pessoa.getInteresse());
        dto.setDescricao(pessoa.getDescricao());
        dto.setTags(pessoa.getTags());
        dto.setCienciaResponsabilidade(pessoa.isCienciaResponsabilidade());

        // Redes sociais
        dto.setInstagram(pessoa.getInstagram());
        dto.setWhatsapp(pessoa.getWhatsapp());
        dto.setTelegram(pessoa.getTelegram());

        // Imagens
        dto.setImagens(imagens);
        dto.setFotoPerfilUrl(fotoPerfilUrl);

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
        dto.setTags(pessoa.getTags());
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