package com.example.pairup.user;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String email;
    private String password;
}