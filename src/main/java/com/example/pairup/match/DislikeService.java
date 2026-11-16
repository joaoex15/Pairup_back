package com.example.pairup.match;

import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DislikeService {

    private final DislikeRepository dislikeRepository;

    /** Idempotente: passar de novo na mesma pessoa não é erro. */
    public void darDislike(String origemId, String destinoId) {
        if (dislikeRepository.existsByPessoaOrigemIdAndPessoaDestinoId(origemId, destinoId)) return;
        Dislike dislike = new Dislike();
        dislike.setPessoaOrigemId(origemId);
        dislike.setPessoaDestinoId(destinoId);
        try {
            dislikeRepository.save(dislike);
        } catch (DuplicateKeyException e) {
            // Duas requisições ao mesmo tempo: a outra já gravou
        }
    }

    public Page<Dislike> listarDislikesDados(String pessoaId, Pageable pageable) {
        return dislikeRepository.findByPessoaOrigemId(pessoaId, pageable);
    }
}
