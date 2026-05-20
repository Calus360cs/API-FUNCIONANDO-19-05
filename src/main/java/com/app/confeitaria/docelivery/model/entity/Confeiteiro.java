package com.app.confeitaria.docelivery.model.entity;

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

    @OneToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER) // Mudamos de ALL para MERGE para gerenciar manualmente no controller
    @JoinColumn(name = "loja_id", referencedColumnName = "id")
    private Loja loja;

    private String proprietario;
    private String categoria;
    private Boolean promocao;
}