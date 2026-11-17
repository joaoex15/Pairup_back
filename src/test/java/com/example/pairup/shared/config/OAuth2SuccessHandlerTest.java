package com.example.pairup.shared.config;

import com.example.pairup.pessoa.PessoaRepository;
import com.example.pairup.shared.security.JwtService;
import com.example.pairup.user.User;
import com.example.pairup.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** O login Google não pode entrar numa conta criada com e-mail e senha. */
class OAuth2SuccessHandlerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PessoaRepository pessoaRepository = mock(PessoaRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final OAuth2SuccessHandler handler = new OAuth2SuccessHandler();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "userRepository", userRepository);
        ReflectionTestUtils.setField(handler, "pessoaRepository", pessoaRepository);
        ReflectionTestUtils.setField(handler, "jwtService", jwtService);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://front");
        when(jwtService.generateToken(any())).thenReturn("tok");
        when(pessoaRepository.findByUsuarioId(any())).thenReturn(Optional.empty());
    }

    private String login(Boolean emailVerificado) throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "123");
        attrs.put("email", "vitima@gmail.com");
        attrs.put("name", "Vítima");
        if (emailVerificado != null) attrs.put("email_verified", emailVerificado);
        DefaultOAuth2User principal = new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, attrs, "sub");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(principal, null));
        return response.getRedirectedUrl();
    }

    @Test
    void contaComSenhaNaoEhVinculada() throws Exception {
        User local = new User("Atacante", "vitima@gmail.com", "hash");
        local.setId("u1");
        when(userRepository.findByEmail("vitima@gmail.com")).thenReturn(Optional.of(local));

        assertThat(login(true)).isEqualTo("http://front/?erro=conta_com_senha");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void emailNaoVerificadoEhRecusado() throws Exception {
        assertThat(login(false)).isEqualTo("http://front/?erro=email_nao_verificado");
        assertThat(login(null)).isEqualTo("http://front/?erro=email_nao_verificado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void contaGoogleExistenteEntra() throws Exception {
        User google = new User("Vítima", "vitima@gmail.com", null);
        google.setId("u2");
        google.setProvider("google");
        when(userRepository.findByEmail("vitima@gmail.com")).thenReturn(Optional.of(google));

        assertThat(login(true)).isEqualTo("http://front/auth/callback#token=tok");
    }

    @Test
    void primeiroLoginCriaContaGoogle() throws Exception {
        when(userRepository.findByEmail("vitima@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId("u3"); return u; });

        assertThat(login(true)).isEqualTo("http://front/auth/callback#token=tok");
        verify(userRepository).save(argThat(u -> "google".equals(u.getProvider())));
    }
}
