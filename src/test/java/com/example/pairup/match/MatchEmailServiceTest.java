package com.example.pairup.match;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.pessoa.PessoaService;
import com.example.pairup.user.User;
import com.example.pairup.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private PessoaService pessoaService;
    @Mock private UserService userService;
    @InjectMocks private MatchEmailService service;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "host", "smtp.teste");
        ReflectionTestUtils.setField(service, "remetente", "nao-responda@pairup.teste");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://pairup.teste");
    }

    private void comPessoas() {
        Pessoa ana = new Pessoa();
        ana.setNome("Ana");
        ana.setEmail("perfil-editavel@teste.com");
        ana.setUsuarioId("user-ana");
        Pessoa bruno = new Pessoa();
        bruno.setNome("Bruno");
        when(pessoaService.findById("ana")).thenReturn(ana);
        when(pessoaService.findById("bruno")).thenReturn(bruno);
    }

    @Test
    void deveEnviarParaOEmailDaContaComONomeDaOutraPessoa() {
        comPessoas();
        when(userService.findById("user-ana")).thenReturn(new User("Ana", "conta@teste.com", null));

        service.notificarMatch("ana", "bruno");

        ArgumentCaptor<SimpleMailMessage> enviada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(enviada.capture());
        assertThat(enviada.getValue().getTo()).containsExactly("conta@teste.com");
        assertThat(enviada.getValue().getFrom()).isEqualTo("nao-responda@pairup.teste");
        assertThat(enviada.getValue().getSubject()).contains("match");
        assertThat(enviada.getValue().getText())
                .contains("Olá, Ana!", "Bruno", "https://pairup.teste/matches");
    }

    @Test
    void naoDeveEnviarSemHostConfigurado() {
        ReflectionTestUtils.setField(service, "host", "");

        service.notificarMatch("ana", "bruno");

        verifyNoInteractions(mailSender, pessoaService, userService);
    }

    @Test
    void naoDeveEnviarSemRemetenteConfigurado() {
        ReflectionTestUtils.setField(service, "remetente", "");

        service.notificarMatch("ana", "bruno");

        verifyNoInteractions(mailSender);
    }

    @Test
    void naoDeveEnviarQuandoAContaFoiExcluida() {
        comPessoas();
        when(userService.findById("user-ana")).thenReturn(null);

        service.notificarMatch("ana", "bruno");

        verifyNoInteractions(mailSender);
    }

    @Test
    void falhaNoEnvioNaoDevePropagar() {
        comPessoas();
        when(userService.findById("user-ana")).thenReturn(new User("Ana", "conta@teste.com", null));
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.notificarMatch("ana", "bruno")).doesNotThrowAnyException();
    }
}
