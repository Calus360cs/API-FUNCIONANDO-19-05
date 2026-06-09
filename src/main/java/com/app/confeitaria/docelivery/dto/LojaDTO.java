package com.app.confeitaria.docelivery.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LojaDTO {
    private Long id;
    private String nomeFantasia;
    private String cnpj;
    private String telefone;
    private String descricao;

    // ADICIONE ESTES DOIS CAMPOS ABAIXO:
    private String endereco;
    private String fotoUrl;
}