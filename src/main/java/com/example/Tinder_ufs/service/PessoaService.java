package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    public List<Pessoa> getAll(){
        return pessoaRepository.findAll();
    }

    public Pessoa findById(String id){
        return pessoaRepository.findById(id).orElse(null);
    }

    public Pessoa create(Pessoa pessoa){
        return pessoaRepository.save(pessoa);
    }

    public Pessoa update(Pessoa pessoa){
        Pessoa existing = findById(pessoa.getId());

        if(existing != null){
            BeanUtils.copyProperties(pessoa, existing, "id");
            return pessoaRepository.save(existing);
        }

        return null;
    }

    public void delete(String id){
        pessoaRepository.deleteById(id);
    }
}