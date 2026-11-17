package com.example.pairup.shared.config;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.user.User;
import com.example.pairup.pessoa.PessoaRepository;
import com.example.pairup.user.UserRepository;
import com.example.pairup.shared.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    private static final String PROVIDER_GOOGLE = "google";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // A sessão só existia para o "state" do OAuth2; a API usa apenas o JWT.
        HttpSession sessao = request.getSession(false);
        if (sessao != null) sessao.invalidate();

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String emailBruto = oauth2User.getAttribute("email");
        String nome = oauth2User.getAttribute("name");

        // Só aceita e-mail que o Google confirmou: é ele que identifica a conta.
        if (emailBruto == null || !Boolean.TRUE.equals(oauth2User.getAttribute("email_verified"))) {
            redirecionarComErro(request, response, "email_nao_verificado");
            return;
        }
        String email = emailBruto.trim().toLowerCase(Locale.ROOT);

        Optional<User> existente = userRepository.findByEmail(email);

        // Conta criada com e-mail e senha: o cadastro local não confirma o e-mail, então
        // qualquer pessoa pode ter registrado esse endereço com uma senha que conhece.
        // Vincular o login Google a ela entregaria a sessão da vítima a quem criou a conta.
        if (existente.isPresent() && !PROVIDER_GOOGLE.equals(existente.get().getProvider())) {
            redirecionarComErro(request, response, "conta_com_senha");
            return;
        }

        User user = existente.orElseGet(() -> {
            User novo = new User(nome, email, null);
            novo.setProvider(PROVIDER_GOOGLE);
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

        String token = jwtService.generateToken(user.getId());

        // Token no fragmento (#), não na query (?): o fragmento não é enviado ao servidor do
        // front, não aparece em logs de acesso/proxy nem no header Referer.
        String redirectUrl = baseUrl() + "/auth/callback#token=" + token;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    /** Volta para a tela de login do front com um código de erro que ela sabe exibir. */
    private void redirecionarComErro(HttpServletRequest request, HttpServletResponse response,
                                     String codigo) throws IOException {
        getRedirectStrategy().sendRedirect(request, response, baseUrl() + "/?erro=" + codigo);
    }

    /** FRONTEND_URL sem barra final, para não gerar //auth/callback. */
    private String baseUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
