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

    @Size(max = 100)
    private String nome;

    @Email
    @NotBlank
    @Size(max = 254)
    private String email;

    // Mínimo de 8 (recomendação NIST/OWASP); máximo de 72: o BCrypt ignora o que passa disso.
    @NotBlank
    @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres")
    private String password;
}
