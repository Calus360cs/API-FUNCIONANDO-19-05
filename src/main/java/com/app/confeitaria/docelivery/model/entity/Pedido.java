package com.app.confeitaria.docelivery.model.entity;

import com.app.confeitaria.docelivery.model.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String numeroPedido;

    private Double valorPedido;

    private LocalDateTime dataHoraPedido;

    private String mercadoPagoTransactionId;

    private Boolean codStatus;

    private Boolean agendado;

    private LocalDateTime dataEntregaAgendada;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    private StatusPedido status;

    // 🟢 CORRIGIDO: Mudado para FetchType.EAGER para carregar os doces do carrinho junto com o pedido
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ItemPedido> itens;

    @ManyToOne
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "confeiteiro_id")
    private Confeiteiro confeiteiro;

    @ManyToOne
    @JoinColumn(name = "entregador_id")
    private Entregador entregador;
}