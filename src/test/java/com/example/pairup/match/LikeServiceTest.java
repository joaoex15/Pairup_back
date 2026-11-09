package com.example.pairup.match;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock private LikeRepository likeRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchEmailService matchEmailService;
    @InjectMocks private LikeService service;

    private Like likeAtivo() {
        Like like = new Like();
        like.setAtivo(true);
        return like;
    }

    private void comLikeReverso() {
        // lenient: as mesmas consultas também rodam com os argumentos invertidos
        lenient().when(likeRepository.findByPessoaOrigemIdAndPessoaDestinoId("bruno", "ana"))
                .thenReturn(Optional.of(likeAtivo()));
    }

    @Test
    void likeSemReciprocidadeNaoDeveEnviarEmail() {
        Map<String, Object> resposta = service.darLike("ana", "bruno");

        assertThat(resposta.get("match")).isEqualTo(false);
        verifyNoInteractions(matchEmailService);
    }

    @Test
    void matchNovoDeveAvisarSoAOutraPessoa() {
        comLikeReverso();

        Map<String, Object> resposta = service.darLike("ana", "bruno");

        assertThat(resposta.get("match")).isEqualTo(true);
        verify(matchRepository).save(any(Match.class));
        verify(matchEmailService).notificarMatch("bruno", "ana");
        verifyNoMoreInteractions(matchEmailService);
    }

    @Test
    void matchJaExistenteNaoDeveEnviarEmailDeNovo() {
        comLikeReverso();
        lenient().when(matchRepository.findByPessoaId1AndPessoaId2("ana", "bruno"))
                .thenReturn(Optional.of(new Match()));

        service.darLike("ana", "bruno");

        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(matchEmailService);
    }
}
