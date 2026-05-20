package com.app.confeitaria.docelivery.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "kit_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // O produto pai (o Kit em si)
    @ManyToOne
    @JoinColumn(name = "kit_id", nullable = false)
    @JsonBackReference // 🟢 Evita o loop infinito (Recursão Infinita) ao serializar para o React
    private Produto kit;

    // O produto filho (o doce/bolo já existente que entra no kit)
    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // Quantidade desse produto específico dentro do kit (ex: 10 brigadeiros)
    @Column(nullable = false)
    private Integer quantidade;
}