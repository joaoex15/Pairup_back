package com.example.Tinder_ufs.security;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        log.debug("Carregando usuário pelo ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Usuário não encontrado com ID: {}", userId);
                    return new UsernameNotFoundException("Usuário não encontrado: " + userId);
                });

        log.debug("Usuário encontrado: {} com email: {}", user.getId(), user.getEmail());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getId())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .authorities(Collections.emptyList())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}