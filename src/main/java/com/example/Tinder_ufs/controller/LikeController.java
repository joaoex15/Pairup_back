package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.service.LikeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}