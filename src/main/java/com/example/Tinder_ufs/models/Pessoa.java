package com.example.Tinder_ufs.models;

import com.example.Tinder_ufs.models.enums.Genero;
import com.example.Tinder_ufs.models.enums.Interesse;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDate;
import java.time.Period;
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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNasc;

    @Indexed(unique = true)
    @Email
    @NotBlank
    private String email;

    @NotNull
    private Genero genero;

    @NotNull
    private Interesse interesse;  // ← ANTIGA sexualidade

    private String descricao;

    private boolean ativo = true;

    // NOVO CAMPO
    private boolean cienciaResponsabilidade = false;

    private String instagram;
    private String whatsapp;
    private String telegram;

    @DBRef
    private List<Tag> tags;

    private String usuarioId;

    public Pessoa() {}

    public Pessoa(String nome, String curso, LocalDate dataNasc, String email) {
        this.nome = nome;
        this.curso = curso;
        this.dataNasc = dataNasc;
        this.email = email;
    }

    // Método para validar idade mínima de 18 anos
    public boolean isMaiorDeIdade() {
        if (dataNasc == null) return false;
        return Period.between(dataNasc, LocalDate.now()).getYears() >= 18;
    }
}