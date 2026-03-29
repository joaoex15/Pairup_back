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

    /**
     * ✅ Retorna apenas o usuário autenticado — leitura via JWT.
     * Removemos getAllUsers() da rota GET / pública; listagem de todos os
     * usuários é dado sensível e não deve ser exposto sem controle de acesso.
     */
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

        // ✅ Nunca retorne a senha — mesmo que esteja hasheada
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    /**
     * Login: retorna um JWT em caso de sucesso.
     * ✅ Retorna JSON estruturado com o token (não apenas uma string).
     */
    @PostMapping("/login")
    @Operation(summary = "Login de usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO loginRequest) {
        try {
            User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());

            // ✅ Gera JWT e devolve ao cliente
            String token = jwtService.generateToken(user.getId().toString());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "email", user.getEmail()
            ));
        } catch (RuntimeException e) {
            // ✅ Mensagem genérica — não revela se o email existe ou não
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciais inválidas."));
        }
    }

    /**
     * Criação de conta (público).
     */
    @PostMapping
    @Operation(summary = "Criar usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<User> createUser(
            @Parameter(description = "Dados do usuário")
            @RequestBody @Valid User user) {

        User created = userService.CreatUser(user);
        created.setPassword(null); // ✅ Nunca retorna a senha
        return ResponseEntity.ok(created);
    }

    /**
     * Atualiza dados do próprio usuário autenticado.
     * ✅ O ID vem do JWT — o cliente não pode atualizar outro usuário.
     */
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

    /**
     * Deleta a própria conta.
     * ✅ O ID vem do JWT.
     */
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