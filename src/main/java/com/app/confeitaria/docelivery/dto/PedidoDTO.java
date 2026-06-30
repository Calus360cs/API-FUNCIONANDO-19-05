package com.app.confeitaria.docelivery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoDTO(
        Long id,
        Long clienteId,       // 🟢 ADICIONADO: Necessário para o PagamentoController localizar o cliente
        Long lojaId,          // 🟢 ADICIONADO: Necessário para vincular à loja correta do confeiteiro
        String nomeCliente,
        String telefoneCliente,
        String enderecoEntrega,
        String status,
        BigDecimal total,
        LocalDateTime dataCriacao,
        List<ItemPedidoDTO> itens
) {}