package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.example.Tinder_ufs.service.TagService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("tags")
@AllArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<Tag>> getAll(){
        return ResponseEntity.ok(tagService.getAll());
    }

    @GetMapping("/ativas")
    public ResponseEntity<List<Tag>> getAtivas(){
        return ResponseEntity.ok(tagService.getAtivas());
    }

    @PostMapping
    public ResponseEntity<Tag> create(@RequestBody Tag tag){
        return ResponseEntity.ok(tagService.create(tag));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id){
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}