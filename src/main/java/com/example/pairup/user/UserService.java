package com.example.pairup.user;

import com.example.pairup.pessoa.PessoaService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PessoaService pessoaService;

    /**
     * Hash de uma senha qualquer. O login compara contra ele quando o e-mail não existe,
     * para levar o mesmo tempo nos dois casos (senão o tempo de resposta revela quais
     * e-mails têm conta).
     */
    private final String hashFalso;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PessoaService pessoaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.pessoaService = pessoaService;
        this.hashFalso = passwordEncoder.encode("senha-inexistente-para-tempo-constante");
    }

    /** E-mail sem espaços e em minúsculas: "Ana@X.com" e "ana@x.com" são a mesma conta. */
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public User findById(String id) {
        if (id == null) return null;
        return userRepository.findById(id).orElse(null);
    }

    /** Busca pelo e-mail normalizado e, para contas antigas gravadas com maiúsculas, pelo original. */
    private Optional<User> buscarPorEmail(String email) {
        String normalizado = normalizarEmail(email);
        Optional<User> user = userRepository.findByEmail(normalizado);
        if (user.isEmpty() && email != null && !email.trim().equals(normalizado)) {
            user = userRepository.findByEmail(email.trim());
        }
        return user;
    }

    public User createUser(UserCreateDTO dados) {
        String email = normalizarEmail(dados.getEmail());
        if (buscarPorEmail(dados.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
        String senha = passwordEncoder.encode(dados.getPassword());
        String nome = dados.getNome() == null ? null : dados.getNome().trim();
        try {
            return userRepository.save(new User(nome, email, senha));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
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

        String novoEmail = normalizarEmail(dados.getEmail());
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
            if (buscarPorEmail(novoEmail).isPresent()) {
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

    /**
     * Mesma resposta e mesmo custo (um BCrypt) para e-mail inexistente, conta Google sem senha
     * e senha errada: nenhum dos três revela se o e-mail tem conta.
     */
    public User login(String email, String password) {
        Optional<User> user = buscarPorEmail(email);
        String hash = user.map(User::getPassword).orElse(null);

        boolean senhaOk = passwordEncoder.matches(password == null ? "" : password,
                hash != null ? hash : hashFalso);

        if (user.isEmpty() || hash == null || !senhaOk) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        return user.get();
    }
}
