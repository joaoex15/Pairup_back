package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    public Page<PessoaPerfilDTO> getAllPerfisWithFilters(Interesse interesse, Genero genero, Pageable pageable) {
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
                .map(this::convertToPerfilDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());

        if (start > filtered.size()) {
            return Page.empty(pageable);
        }

        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

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

    public Pessoa create(Pessoa pessoa) {
        if (!pessoa.isMaiorDeIdade()) {
            throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
        }
        return pessoaRepository.save(pessoa);
    }

    public Pessoa update(Pessoa pessoa) {
        Pessoa existing = findById(pessoa.getId());

        if (existing != null) {
            if (pessoa.getDataNasc() != null && !pessoa.getDataNasc().equals(existing.getDataNasc())) {
                if (!pessoa.isMaiorDeIdade()) {
                    throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
                }
            }
            BeanUtils.copyProperties(pessoa, existing, "id", "usuarioId");
            return pessoaRepository.save(existing);
        }
        return null;
    }

    public void delete(String id) {
        pessoaRepository.deleteById(id);
    }

    public Pessoa marcarCienciaResponsabilidade(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            throw new RuntimeException("Pessoa não encontrada");
        }
        pessoa.setCienciaResponsabilidade(true);
        return pessoaRepository.save(pessoa);
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