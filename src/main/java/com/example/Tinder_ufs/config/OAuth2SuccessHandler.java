package com.example.Tinder_ufs.config;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String nome  = oauth2User.getAttribute("name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User novo = new User(nome, email, null);
            return userRepository.save(novo);
        });

        // Usa variável de ambiente — fallback para localhost em dev
        String frontendUrl = System.getenv("FRONTEND_URL") != null
                ? System.getenv("FRONTEND_URL")
                : "http://localhost:5173";

        String redirectUrl = frontendUrl + "/auth/callback"
                + "?userId=" + user.getId()
                + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&name="  + URLEncoder.encode(nome,  StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}