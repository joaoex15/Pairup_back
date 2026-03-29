package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.dto.PessoaPerfilDTO;
import com.example.Tinder_ufs.dto.PessoaRedesSociaisDTO;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    // Retorna lista de PERFIS (sem redes sociais) com filtros
    public List<PessoaPerfilDTO> getAllPerfisWithFilters(Interesse interesse, Genero genero) {
        List<Pessoa> pessoas = pessoaRepository.findByAtivoTrue();

        return pessoas.stream()
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
    }

    // Retorna PERFIL de uma pessoa específica (sem redes sociais)
    public PessoaPerfilDTO getPerfilById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        return convertToPerfilDTO(pessoa);
    }

    // Retorna REDES SOCIAIS de uma pessoa (apenas após match)
    public PessoaRedesSociaisDTO getRedesSociaisById(String id) {
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            return null;
        }
        return convertToRedesSociaisDTO(pessoa);
    }

    // Busca pessoa completa por ID (uso interno)
    public Pessoa findById(String id){
        return pessoaRepository.findById(id).orElse(null);
    }

    // Cria nova pessoa com validação de idade
    public Pessoa create(Pessoa pessoa){
        if (!pessoa.isMaiorDeIdade()) {
            throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
        }
        return pessoaRepository.save(pessoa);
    }

    // Atualiza pessoa com validação de idade
    public Pessoa update(Pessoa pessoa){
        Pessoa existing = findById(pessoa.getId());

        if (existing != null){
            if (pessoa.getDataNasc() != null && !pessoa.getDataNasc().equals(existing.getDataNasc())) {
                if (!pessoa.isMaiorDeIdade()) {
                    throw new RuntimeException("A pessoa deve ter pelo menos 18 anos");
                }
            }
            BeanUtils.copyProperties(pessoa, existing, "id");
            return pessoaRepository.save(existing);
        }
        return null;
    }

    // Deleta pessoa
    public void delete(String id){
        pessoaRepository.deleteById(id);
    }

    // Marca ciência de responsabilidade
    public Pessoa marcarCienciaResponsabilidade(String id){
        Pessoa pessoa = findById(id);
        if (pessoa == null) {
            throw new RuntimeException("Pessoa não encontrada");
        }
        pessoa.setCienciaResponsabilidade(true);
        return pessoaRepository.save(pessoa);
    }

    // Converte Pessoa → PessoaPerfilDTO (remove redes sociais e flags internas)
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

    // Converte Pessoa → PessoaRedesSociaisDTO (apenas redes sociais)
    private PessoaRedesSociaisDTO convertToRedesSociaisDTO(Pessoa pessoa) {
        PessoaRedesSociaisDTO dto = new PessoaRedesSociaisDTO();
        dto.setId(pessoa.getId());
        dto.setInstagram(pessoa.getInstagram());
        dto.setWhatsapp(pessoa.getWhatsapp());
        dto.setTelegram(pessoa.getTelegram());
        return dto;
    }
}