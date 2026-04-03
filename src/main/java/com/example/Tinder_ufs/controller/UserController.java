package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.LoginRequestDTO;
import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.security.JwtService;
import com.example.Tinder_ufs.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("users")
@AllArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/me")
    @Operation(summary = "Obter usuário logado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO loginRequest) {
        try {
            User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());

            String token = jwtService.generateToken(user.getId().toString());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "email", user.getEmail()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciais inválidas."));
        }
    }

    @PostMapping
    @Operation(summary = "Criar usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<?> createUser(@RequestBody @Valid User user) {
        try {
            User created = userService.createUser(user);
            created.setPassword(null);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar meu usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário atualizado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> updateMe(
            @RequestBody @Valid User user,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        User updated = userService.updateUser(userId, user);
        updated.setPassword(null);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deletar minha conta")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta deletada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> deleteMe(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido.");
        }

        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}