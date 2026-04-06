package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.LoginRequestDTO;
import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.security.JwtService;
import com.example.Tinder_ufs.security.SecurityUtils;
import com.example.Tinder_ufs.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("users")
@AllArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final JwtService jwtService;

    /**
     * Retorna os dados do usuário autenticado (sem senha).
     */
    @GetMapping("/me")
    @Operation(summary = "Obter usuário logado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        User user = userService.findById(userId);
        if (user == null) return ResponseEntity.notFound().build();

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    /**
     * Autentica um usuário e retorna o JWT.
     *
     * ✅ CORRIGIDO: captura BadCredentialsException (lançada pelo UserService via Spring Security)
     *    em vez de ResponseStatusException — evitava que o catch nunca fosse acionado,
     *    fazendo login inválido retornar 500 em vez de 401.
     *
     * TODO: adicionar rate limiting por IP (Bucket4j / Resilience4j).
     *       Após 5 falhas consecutivas, bloquear por 15 min ou exigir CAPTCHA.
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
            String token = jwtService.generateToken(user.getId().toString());

            log.info("[AUDIT] Login bem-sucedido para userId={}", user.getId());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "email", user.getEmail()
            ));

        } catch (BadCredentialsException e) {
            // ✅ CORRIGIDO: era ResponseStatusException — nunca batia aqui, retornava 500
            log.warn("[AUDIT] Tentativa de login falhou para email={}", loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Credenciais inválidas."));
        }
        // Qualquer outro erro (banco, etc.) NÃO é capturado aqui.
        // Ele sobe para o GlobalExceptionHandler que retorna 500 sem stack trace.
    }

    /**
     * Cria um novo usuário.
     * ✅ Conflito de e-mail retorna 409 — distinção clara de 400 (dados inválidos).
     */
    @PostMapping
    @Operation(summary = "Criar usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    })
    public ResponseEntity<?> createUser(@RequestBody @Valid User user) {
        try {
            User created = userService.createUser(user);
            created.setPassword(null);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Atualiza dados do usuário autenticado.
     * ✅ userId vem do JWT — o cliente não pode alterar dados de outro usuário.
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

        String userId = SecurityUtils.getUserIdOrThrow(request);

        User updated = userService.updateUser(userId, user);
        if (updated == null) return ResponseEntity.notFound().build();
        updated.setPassword(null);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deleta a conta do usuário autenticado.
     * ✅ Log de auditoria antes da exclusão.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Deletar minha conta")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta deletada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Void> deleteMe(HttpServletRequest request) {
        String userId = SecurityUtils.getUserIdOrThrow(request);

        log.info("[AUDIT] Conta deletada para userId={}", userId);
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}