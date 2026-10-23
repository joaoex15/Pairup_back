package com.example.pairup.tag;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TagRepository extends MongoRepository<Tag, String> {

    List<Tag> findByAtivaTrue();

    List<Tag> findByIdInAndAtivaTrue(List<String> ids);
}