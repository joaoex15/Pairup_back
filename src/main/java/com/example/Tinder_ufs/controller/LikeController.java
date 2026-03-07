package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Like;
import com.example.Tinder_ufs.service.LikeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("likes")
@AllArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ResponseEntity<Void> darLike(
            @RequestParam String origemId,
            @RequestParam String destinoId
    ){
        likeService.darLike(origemId, destinoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dados/{pessoaId}")
    public ResponseEntity<List<Like>> listarLikesDados(@PathVariable String pessoaId) {
        List<Like> likes = likeService.listarLikesDados(pessoaId);
        return ResponseEntity.ok(likes);
    }

    @GetMapping("/recebidos/{pessoaId}")
    public ResponseEntity<List<Like>> listarLikesRecebidos(@PathVariable String pessoaId) {
        List<Like> likes = likeService.listarLikesRecebidos(pessoaId);
        return ResponseEntity.ok(likes);
    }
}