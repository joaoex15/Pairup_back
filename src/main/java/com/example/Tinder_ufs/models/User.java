package com.example.Tinder_ufs.models;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.Data;

@Document(collection = "user")
@Data
public class User {
    @Id
    private String id;
    @NotBlank
    private String nome;
    @Email
    @NotBlank
    private String email;
    @Past
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dataNascimento;

    private String password;

    public User(String nome, String email, LocalDate dataNascimento, String password) {
        this.nome = nome;
        this.email = email;
        this.dataNascimento = dataNascimento;
        this.password = password;
    }
}

