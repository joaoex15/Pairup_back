package com.example.pairup.service;

import com.example.pairup.models.Tag;
import com.example.pairup.repositories.TagRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<Tag> getAll() {
        return tagRepository.findAll();
    }

    public Tag create(Tag tag) {
        return tagRepository.save(tag);
    }

    public List<Tag> getAtivas() {
        return tagRepository.findByAtivaTrue();
    }

    public void delete(String id) {
        tagRepository.deleteById(id);
    }
}