package com.example.pairup.pessoa;

import com.example.pairup.match.PessoaRedesSociaisDTO;
import com.example.pairup.imagem.Imagem;
import com.example.pairup.tag.Tag;
import com.example.pairup.tag.TagRepository;
import com.example.pairup.imagem.ImagemRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final TagRepository tagRepository;

    // ==================== BUSCAS COMPLETAS ====================

    @Transactional(readOnly = true)
    public PessoaCompletaDTO getPessoaCompletaByUsuarioId(String usuarioId) {
        Pessoa pessoa = findByUsuarioId(usuarioId);
        if (pessoa == null) return null;
        return convertToCompletaDTO(pessoa);
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

        // 3. Montar DTOs com as imagens
        List<PessoaPerfilDTO> dtos = page.getContent().stream()
                .map(pessoa -> convertToPerfilDTO(pessoa,
                        imagensPorPessoa.getOrDefault(pessoa.getId(), List.of())))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    // ==================== CRUD COM VALIDAÇÃO ====================

    @Transactional
    public Pessoa create(Pessoa pessoa) {
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

    /**
     * Converte IDs de tag em referências; IDs inexistentes ou de tags inativas → 400.
     */
    public List<Tag> resolverTags(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return new ArrayList<>();
        List<String> idsUnicos = tagIds.stream().distinct().toList();
        List<Tag> tags = tagRepository.findByIdInAndAtivaTrue(idsUnicos);
        if (tags.size() != idsUnicos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag inválida ou inativa");
        }
        return tags;
    }

    // ==================== MÉTODOS BÁSICOS ====================

    public PessoaPerfilDTO getPerfilById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) return null;
        return convertToPerfilDTO(pessoa, imagemRepository.findByPessoaAndAtivaTrue(pessoa));
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

    /**
     * Pessoa.id do perfil da conta, ou 400 se o usuário ainda não criou o perfil.
     */
    public String getPessoaIdOrThrow(String usuarioId) {
        Pessoa pessoa = findByUsuarioId(usuarioId);
        if (pessoa == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Perfil não encontrado. Complete seu cadastro primeiro.");
        }
        return pessoa.getId();
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
        PessoaCompletaDTO dto = preencherPerfil(new PessoaCompletaDTO(), pessoa,
                imagemRepository.findByPessoaAndAtivaTrue(pessoa));
        dto.setCienciaResponsabilidade(pessoa.isCienciaResponsabilidade());
        dto.setInstagram(pessoa.getInstagram());
        dto.setWhatsapp(pessoa.getWhatsapp());
        dto.setTelegram(pessoa.getTelegram());
        return dto;
    }

    private PessoaPerfilDTO convertToPerfilDTO(Pessoa pessoa, List<Imagem> imagens) {
        return preencherPerfil(new PessoaPerfilDTO(), pessoa, imagens);
    }

    /** Campos comuns a PessoaPerfilDTO e PessoaCompletaDTO. */
    private <T extends PessoaPerfilDTO> T preencherPerfil(T dto, Pessoa pessoa, List<Imagem> imagens) {
        dto.setId(pessoa.getId());
        dto.setNome(pessoa.getNome());
        dto.setCurso(pessoa.getCurso());
        dto.setDataNasc(pessoa.getDataNasc());
        dto.setEmail(pessoa.getEmail());
        dto.setGenero(pessoa.getGenero());
        dto.setInteresse(pessoa.getInteresse());
        dto.setDescricao(pessoa.getDescricao());
        dto.setTags(pessoa.getTags() == null ? new ArrayList<>()
                : pessoa.getTags().stream().map(Tag::getNome).collect(Collectors.toList()));
        dto.setImagens(imagens);
        dto.setFotoPerfilUrl(imagens.stream()
                .filter(Imagem::isPerfil)
                .map(Imagem::getUrl)
                .findFirst()
                .orElse(null));
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