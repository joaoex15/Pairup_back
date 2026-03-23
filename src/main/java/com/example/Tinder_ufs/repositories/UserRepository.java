package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    // ADICIONE ESTE MÉTODO
    Optional<User> findByEmail(String email);
}