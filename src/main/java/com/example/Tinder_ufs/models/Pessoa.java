package com.example.Tinder_ufs.models;

import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Sexualidade;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDate;
import java.util.List;
@Document(collection = "pessoa")
@Data
public class Pessoa {

    @Id
    private String id;

    @NotBlank
    private String nome;

    private String curso;

    @Past
    @JsonFormat(pattern = "yyyy-MM-dd")   // frontend envia nesse formato
    private LocalDate dataNasc;

    @Email
    @NotBlank
    private String email;

    @NotNull
    private Genero genero;

    @NotNull
    private Sexualidade sexualidade;

    private String descricao;

    private boolean ativo = true;

    private String instagram;
    private String whatsapp;
    private String telegram;

    // ✅ Tags: @DBRef correto pois Tag é uma entidade
    @DBRef
    private List<Tag> tags;

    // ✅ Apenas o ID do usuário — sem @DBRef, sem @NotBlank
    private String usuarioId;

    public Pessoa() {}

    public Pessoa(String nome, String curso, LocalDate dataNasc, String email) {
        this.nome = nome;
        this.curso = curso;
        this.dataNasc = dataNasc;
        this.email = email;
    }
}