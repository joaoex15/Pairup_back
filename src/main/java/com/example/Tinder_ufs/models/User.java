package com.example.Tinder_ufs.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "users")
@Data
@NoArgsConstructor
public class User {
    @Id
    private String id;

    private String nome;

    @Email
    @NotBlank
    @Indexed(unique = true)
    private String email;

    private String password;
    private String provider;

    public User(String nome, String email, String password) {
        this.nome = nome;
        this.email = email;
        this.password = password;
    }
}