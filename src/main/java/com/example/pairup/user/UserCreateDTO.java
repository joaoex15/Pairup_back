package com.example.pairup.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Corpo de POST /users. Não aceita id nem provider: com id preenchido, o save
 * substituiria a conta existente.
 */
@Data
public class UserCreateDTO {

    private String nome;

    @Email
    @NotBlank
    private String email;

    // Máximo de 72: o BCrypt ignora o que passa disso.
    @NotBlank
    @Size(min = 6, max = 72)
    private String password;
}
