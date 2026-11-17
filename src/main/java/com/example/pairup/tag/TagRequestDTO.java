package com.example.pairup.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Corpo de POST /tags. Sem "id": com a entidade Tag no @RequestBody, enviar o id de uma
 * tag existente fazia o save sobrescrevê-la.
 */
@Data
public class TagRequestDTO {

    @NotBlank
    @Size(max = 50)
    private String nome;

    @Size(max = 200)
    private String descricao;

    @NotBlank
    @Size(max = 50)
    private String categoria;

    private boolean ativa = true;
}
