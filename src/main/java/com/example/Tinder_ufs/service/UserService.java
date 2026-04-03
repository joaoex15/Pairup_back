package com.example.Tinder_ufs.service;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.repositories.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException; // Correção: Import correto para o Spring Data MongoDB
import org.springframework.security.authentication.BadCredentialsException; // Correção: Exceção de segurança adequada
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Boa prática: manter como 'final'

    public List<User> getAllUsers(){
        return userRepository.findAll();
    }

    public User findById(String id){
        return userRepository.findById(id).orElse(null);
    }

    // Correção: Padrão camelCase para nome de métodos em Java
    public User createUser(User user) {
        // Verifica se email já existe antes de tentar salvar
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    // Correção: Assinatura alterada para receber ID e dados, compatível com o UserController
    public User updateUser(String id, User userUpdates){
        User existingUser = findById(id);

        if (existingUser != null){
            // Correção Crítica (Segurança): Remoção do BeanUtils.copyProperties
            // Atualize apenas os campos permitidos manualmente para evitar Mass Assignment
            if (userUpdates.getNome() != null) {
                existingUser.setNome(userUpdates.getNome());
            }
            if (userUpdates.getEmail() != null) {
                existingUser.setEmail(userUpdates.getEmail());
            }

            // Adicione outros campos seguros aqui (ex: bio, idade, genero).
            // NUNCA atualize a senha ou ID por este método diretamente.

            try {
                return userRepository.save(existingUser);
            } catch (DuplicateKeyException e) {
                throw new IllegalArgumentException("Email já cadastrado por outro usuário");
            }
        }
        return null;
    }

    public void deleteUser(String id){
        userRepository.deleteById(id); // Removidos parênteses redundantes
    }

    public User login(String email, String password) {
        // Correção de Segurança: Retornar a mesma mensagem para falha de email ou senha
        // Isso evita que invasores descubram quais emails estão cadastrados na plataforma
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        } else {
            throw new BadCredentialsException("Credenciais inválidas");
        }
    }
}