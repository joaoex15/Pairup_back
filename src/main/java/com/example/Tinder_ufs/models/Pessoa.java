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
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dataNasc;

    private String periodo;

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

    // Lista de tags
    @DBRef
    private List<Tag> tags;

    // Referência ao usuário dono da conta
    @NotBlank
    private String usuarioId;

    public Pessoa(String nome, String curso, LocalDate dataNasc, String periodo, String email) {
        this.nome = nome;
        this.curso = curso;
        this.dataNasc = dataNasc;
        this.periodo = periodo;
        this.email = email;
    }
}