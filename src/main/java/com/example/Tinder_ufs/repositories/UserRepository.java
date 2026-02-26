package com.example.Tinder_ufs.repositories;

import com.example.Tinder_ufs.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository  extends MongoRepository<User,String> {

}
