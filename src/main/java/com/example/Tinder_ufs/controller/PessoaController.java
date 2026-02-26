package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.service.PessoaService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("pessoas")
@AllArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    public ResponseEntity<List<Pessoa>> getAll(){
        return ResponseEntity.ok(pessoaService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pessoa> getById(@PathVariable String id){
        return ResponseEntity.ok(pessoaService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Pessoa> create(@RequestBody Pessoa pessoa){
        return ResponseEntity.ok(pessoaService.create(pessoa));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pessoa> update(@PathVariable String id, @RequestBody Pessoa pessoa){
        pessoa.setId(id);
        return ResponseEntity.ok(pessoaService.update(pessoa));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id){
        pessoaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}