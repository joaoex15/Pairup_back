package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Match;
import com.example.Tinder_ufs.service.MatchService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("matches")
@AllArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/{pessoaId}")
    public ResponseEntity<List<Match>> listarMeusMatches(@PathVariable String pessoaId){
        return ResponseEntity.ok(matchService.listarMeusMatches(pessoaId));
    }

    @PutMapping("/desfazer/{id}")
    public ResponseEntity<Void> desfazerMatch(@PathVariable String id){
        matchService.desfazerMatch(id);
        return ResponseEntity.ok().build();
    }
}