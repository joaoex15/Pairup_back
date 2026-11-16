package com.example.pairup.user;

import com.example.pairup.pessoa.PessoaService;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PessoaService pessoaService;

    public User findById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    public User createUser(UserCreateDTO dados) {
        if (userRepository.findByEmail(dados.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
        String senha = passwordEncoder.encode(dados.getPassword());
        return userRepository.save(new User(dados.getNome(), dados.getEmail(), senha));
    }

    /**
     * Atualiza nome e e-mail. Trocar o e-mail exige a senha atual e só vale para conta com
     * senha: na conta Google, o e-mail é o que o Google confirmou e não pode ser trocado aqui.
     */
    public User updateUser(String id, UserUpdateDTO dados) {
        User existingUser = findById(id);
        if (existingUser == null) return null;

        if (dados.getNome() != null && !dados.getNome().isBlank()) {
            existingUser.setNome(dados.getNome().trim());
        }

        String novoEmail = dados.getEmail() == null ? null : dados.getEmail().trim();
        if (novoEmail != null && !novoEmail.isBlank() && !novoEmail.equalsIgnoreCase(existingUser.getEmail())) {
            if (existingUser.getPassword() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Conta do Google: o e-mail é o da conta Google e não pode ser trocado");
            }
            if (dados.getSenhaAtual() == null
                    || !passwordEncoder.matches(dados.getSenhaAtual(), existingUser.getPassword())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha atual incorreta");
            }
            // O índice único nem sempre existe no banco: confere antes de gravar
            if (userRepository.findByEmail(novoEmail).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já cadastrado por outro usuário");
            }
            existingUser.setEmail(novoEmail);
        }

        try {
            return userRepository.save(existingUser);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já cadastrado por outro usuário");
        }
    }

    /** Apaga a conta e o perfil vinculado; antes o perfil ficava ativo no feed. */
    public void deleteUser(String id) {
        pessoaService.deleteByUsuarioId(id);
        userRepository.deleteById(id);
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        } else {
            throw new BadCredentialsException("Credenciais inválidas");
        }
    }
}