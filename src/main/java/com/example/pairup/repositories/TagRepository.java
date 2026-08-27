package com.example.pairup.repositories;

import com.example.pairup.models.Tag;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TagRepository extends MongoRepository<Tag, String> {

    List<Tag> findByAtivaTrue();

    List<Tag> findByCategoria(String categoria);
}