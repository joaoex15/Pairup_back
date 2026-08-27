package com.example.pairup.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String email;
    private String password;
}