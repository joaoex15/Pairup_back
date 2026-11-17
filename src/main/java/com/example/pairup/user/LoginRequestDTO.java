package com.example.pairup.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank
    @Size(max = 254)
    private String email;

    // Limite alto o bastante para qualquer senha válida, mas evita corpo gigante no BCrypt
    @NotBlank
    @Size(max = 128)
    private String password;
}
