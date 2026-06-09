package com.app.confeitaria.docelivery.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@DiscriminatorValue("CONFEITEIRO") // Vincula com a coluna tipo_usuario
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true) // 🟢 CRÍTICO: Faz o Lombok incluir os getters/setters da classe pai Usuario
@ToString(callSuper = true)
public class Confeiteiro extends Usuario {

    @OneToOne(mappedBy = "confeiteiro", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("confeiteiro") // Evita recursão infinita no JSON
    private Loja loja;

    private String proprietario;
    private String categoria;
    private Boolean promocao;
}