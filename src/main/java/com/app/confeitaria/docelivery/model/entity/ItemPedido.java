package com.app.confeitaria.docelivery.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "itemPedido")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private int quantidade;

    @Column(nullable = false)
    private double precoUnitario; // Preço do doce no dia da compra

    @Column(nullable = false)
    private double precoTotal;    // Calculado: quantidade * precoUnitario

    // RELACIONAMENTOS

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido; // O item precisa saber a qual pedido pertence

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto; // O item precisa saber qual doce foi vendido


}
