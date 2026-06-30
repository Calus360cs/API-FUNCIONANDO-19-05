package com.app.confeitaria.docelivery.dto;

import com.app.confeitaria.docelivery.model.entity.Produto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // ADICIONADO: Importação do Jackson
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// CORREÇÃO SÊNIOR: Esta anotação instrui o ObjectMapper a aceitar o payload JSON
// mesmo que o frontend envie chaves adicionais (como "confeiteiro"). O Spring vai
// mapear tudo o que ele conhece e descartar o resto sem estourar Exceptions.
@JsonIgnoreProperties(ignoreUnknown = true)
public class LojaDTO {
    private Long id;
    private String nomeFantasia;
    private String cnpj;
    private String telefone;
    private String descricao;
    private String endereco;
    private String fotoUrl;
    private List<Produto> produtos;
}