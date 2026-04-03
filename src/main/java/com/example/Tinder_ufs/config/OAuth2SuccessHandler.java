package com.example.Tinder_ufs.config;

import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.repositories.PessoaRepository;
import com.example.Tinder_ufs.repositories.UserRepository;
import com.example.Tinder_ufs.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String nome = oauth2User.getAttribute("name");

        // Busca o usuário ou cria um novo se for o primeiro login pelo Google
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User novo = new User(nome, email, null);
            novo.setProvider("google");
            return userRepository.save(novo);
        });

        // Verificar se já existe uma Pessoa vinculada a este User
        Optional<Pessoa> pessoaExistente = pessoaRepository.findByUsuarioId(user.getId());
        if (pessoaExistente.isEmpty()) {
            // Criar Pessoa básica para completar cadastro depois
            Pessoa novaPessoa = new Pessoa();
            novaPessoa.setNome(nome);
            novaPessoa.setEmail(email);
            novaPessoa.setUsuarioId(user.getId());
            novaPessoa.setAtivo(true);
            pessoaRepository.save(novaPessoa);
        }

        String token = jwtService.generateToken(user.getId().toString());

        String frontendUrl = System.getenv("FRONTEND_URL") != null
                ? System.getenv("FRONTEND_URL")
                : "http://localhost:5173";

        String redirectUrl = frontendUrl + "/auth/callback?token=" + token;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}