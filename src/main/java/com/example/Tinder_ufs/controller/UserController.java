package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.LoginRequestDTO;
import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("users")
@AllArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários do sistema")
public class UserController {
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Listar todos os usuários", description = "Retorna uma lista com todos os usuários cadastrados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de usuários retornada com sucesso")
    })
    public ResponseEntity<List<User>> getAllUsers(){
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar usuário por ID", description = "Retorna os detalhes de um usuário específico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<User> getUserById(
            @Parameter(description = "ID do usuário", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        User user = userService.findById(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    // NOVO ENDPOINT DE LOGIN (apenas isso foi adicionado)
    @PostMapping("/login")
    @Operation(summary = "Login de usuário", description = "Autentica um usuário no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Senha incorreta"),
            @ApiResponse(responseCode = "404", description = "Email não encontrado")
    })
    public ResponseEntity<User> login(@RequestBody LoginRequestDTO loginRequest) {
        try {
            User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
            user.setPassword(null); // Não enviar a senha
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Email não encontrado")) {
                return ResponseEntity.status(404).build();
            } else if (e.getMessage().equals("Senha incorreta")) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    @Operation(summary = "Criar novo usuário", description = "Cadastra um novo usuário no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<User> createUser(
            @Parameter(description = "Dados do usuário", required = true)
            @RequestBody User user){
        return ResponseEntity.ok(userService.CreatUser(user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar usuário", description = "Atualiza os dados de um usuário existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário atualizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<User> updateUserById(
            @Parameter(description = "ID do usuário", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id,

            @Parameter(description = "Dados atualizados do usuário", required = true)
            @RequestBody User user){
        user.setId(id);
        User updatedUser = userService.Update(user);
        if (updatedUser != null) {
            return ResponseEntity.ok(updatedUser);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar usuário", description = "Remove um usuário do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Usuário deletado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<Void> deleteUserById(
            @Parameter(description = "ID do usuário", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String id){
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}