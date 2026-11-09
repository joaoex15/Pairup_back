package com.example.pairup.match;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.pessoa.PessoaService;
import com.example.pairup.user.User;
import com.example.pairup.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Avisa por e-mail que a pessoa recebeu um match.
 * Assíncrono e sem propagar erro: um SMTP lento ou fora do ar não pode afetar o like.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEmailService {

    private final JavaMailSender mailSender;
    private final PessoaService pessoaService;
    private final UserService userService;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${notificacao.email.remetente:}")
    private String remetente;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void notificarMatch(String destinatarioPessoaId, String outraPessoaId) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(remetente)) {
            log.debug("E-mail de match não enviado: MAIL_HOST ou MAIL_FROM não configurado");
            return;
        }

        try {
            Pessoa destinatario = pessoaService.findById(destinatarioPessoaId);
            Pessoa outraPessoa = pessoaService.findById(outraPessoaId);
            // O e-mail da conta, não o do perfil: o do perfil é editável e não é verificado,
            // e o perfil continua existindo depois de DELETE /users/me.
            User conta = destinatario == null ? null : userService.findById(destinatario.getUsuarioId());
            if (conta == null || outraPessoa == null || !StringUtils.hasText(conta.getEmail())) {
                log.warn("E-mail de match não enviado: conta não encontrada para pessoaId={}", destinatarioPessoaId);
                return;
            }

            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(conta.getEmail());
            mensagem.setSubject("Você recebeu um match no PairUp!");
            mensagem.setText(montarTexto(destinatario.getNome(), outraPessoa.getNome()));
            mailSender.send(mensagem);

            log.info("[AUDIT] E-mail de match enviado para pessoaId={}", destinatarioPessoaId);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de match para pessoaId={}: {}", destinatarioPessoaId, e.getMessage());
        }
    }

    private String montarTexto(String nomeDestinatario, String nomeOutraPessoa) {
        return """
                Olá, %s!

                Você e %s curtiram um ao outro: é um match!

                Veja seus matches em %s/matches

                Equipe PairUp
                """.formatted(nomeDestinatario, nomeOutraPessoa, frontendUrl);
    }
}
