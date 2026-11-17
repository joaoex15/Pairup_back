package com.example.pairup.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Corpo de PUT /users/me. Campo ausente mantém o valor atual.
 * Trocar o e-mail exige a senha atual: o e-mail é o que identifica a conta no login,
 * e um token roubado não pode bastar para tomar a conta de vez.
 */
@Data
public class UserUpdateDTO {

    @Size(max = 100)
    private String nome;

    @Email
    @Size(max = 254)
    private String email;

    @Size(max = 128)
    private String senhaAtual;
}
