package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.PedidoDTO;
import com.app.confeitaria.docelivery.model.entity.ItemPedido;
import com.app.confeitaria.docelivery.model.entity.Pedido;
import com.app.confeitaria.docelivery.model.enums.StatusPedido;
import com.app.confeitaria.docelivery.model.repository.PedidoRepository;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate; // ADICIONADO PARA TEMPO REAL
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Primeiro método: Atualiza usando o ENUM seguro (Ideal para a API/React)
    public PedidoDTO atualizarStatus(Long pedidoId, StatusPedido novoStatus) {
        // 1. Busca no banco
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // 2. Agora funciona perfeitamente porque 'status' espera um StatusPedido!
        pedido.setStatus(novoStatus);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // 3. Converte para DTO
        PedidoDTO pedidoDTO = converterParaDTO(pedidoSalvo);

        // 4. Dispara para o WebSocket
        messagingTemplate.convertAndSend("/topico/pedidos", pedidoDTO);

        return pedidoDTO;
    }

    private PedidoDTO converterParaDTO(Pedido pedido) {
        if (pedido == null) return null;

        String nomeDoCliente = (pedido.getCliente() != null) ? pedido.getCliente().getNome() : "Cliente não informado";
        String telefoneDoCliente = (pedido.getCliente() != null) ? pedido.getCliente().getTelefone() : "";
        java.math.BigDecimal total = java.math.BigDecimal.valueOf(pedido.getValorPedido());

        // Pega o Enum com getStatus() e transforma em String para o DTO
        String statusStr = (pedido.getStatus() != null) ? pedido.getStatus().name() : "NOVO";

        return new PedidoDTO(
                pedido.getId(),
                nomeDoCliente,
                telefoneDoCliente,
                "Retirada na Loja / Ver cadastro",
                statusStr,
                total,
                pedido.getDataHoraPedido(),
                java.util.Collections.emptyList()
        );
    }

    @Transactional
    public Pedido realizarPedido(Pedido pedido) {
        String codigoUnico = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        pedido.setNumeroPedido("DOCE-" + codigoUnico);
        pedido.setDataHoraPedido(LocalDateTime.now());

        if (pedido.isAgendado()) {
            // CORRIGIDO: Convertido String para o tipo correto Enum StatusPedido
            pedido.setStatus(StatusPedido.valueOf("AGENDADO"));
            if (pedido.getDataEntregaAgendada() != null &&
                    pedido.getDataEntregaAgendada().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("A data do agendamento não pode ser no passado.");
            }
        } else {
            // CORRIGIDO: Convertido String para o tipo correto Enum StatusPedido
            pedido.setStatus(StatusPedido.valueOf("NOVO")); // Alterado de PENDENTE para NOVO para bater com as opções do seu Enum
        }

        pedido.setCodStatus(true);
        double valorTotalGeral = 0;

        if (pedido.getItens() != null && !pedido.getItens().isEmpty()) {
            for (ItemPedido item : pedido.getItens()) {
                item.setPedido(pedido);

                var produtoRef = produtoRepository.findById(item.getProduto().getId())
                        .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + item.getProduto().getId()));

                item.setPrecoUnitario(produtoRef.getPreco());
                double subtotal = item.getQuantidade() * item.getPrecoUnitario();
                item.setPrecoTotal(subtotal);
                valorTotalGeral += subtotal;

                if (produtoRef.getEstoque() < item.getQuantidade()) {
                    throw new RuntimeException("Estoque insuficiente para o produto: " + produtoRef.getNome());
                }

                // Baixa o estoque na memória (o @Transactional salva no banco no final automaticamente)
                produtoRef.setEstoque(produtoRef.getEstoque() - item.getQuantidade());
            }
        }

        pedido.setValorPedido(valorTotalGeral);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // --- DISPARO EM TEMPO REAL PARA O CONFEITEIRO ---
        if (pedidoSalvo.getConfeiteiro() != null) {
            String destino = "/topico/confeiteiro/" + pedidoSalvo.getConfeiteiro().getId() + "/pedidos";
            messagingTemplate.convertAndSend(destino, pedidoSalvo);
        }

        return pedidoSalvo;
    }

    public List<Pedido> buscarFilaConfeiteiro(Long confeiteiroId, List<String> status) {
        // CORRIGIDO: Transforma a lista de Strings recebidas em uma lista de Enums válidos para o banco ler sem dar erro
        List<StatusPedido> statusEnums = status.stream()
                .map(s -> StatusPedido.valueOf(s.toUpperCase()))
                .collect(Collectors.toList());

        return pedidoRepository.findByConfeiteiroIdAndStatusInOrderByDataHoraPedidoAsc(confeiteiroId, statusEnums);
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
    }

    @Transactional
    // CORRIGIDO: Mudança no nome do método para evitar duplicidade de assinatura no Java
    public Pedido atualizarStatusViaString(Long id, String novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado com o ID: " + id));

        String statusMaiusculo = novoStatus.toUpperCase();

        // CORRIGIDO: Convertido String para o tipo correto Enum StatusPedido antes de salvar
        pedido.setStatus(StatusPedido.valueOf(statusMaiusculo));

        if (statusMaiusculo.equals("ENTREGUE") || statusMaiusculo.equals("CANCELADO")) {
            pedido.setCodStatus(false);

            // REGRINHA EXTRA: Se o pedido for cancelado, devolve os itens ao estoque
            if (statusMaiusculo.equals("CANCELADO") && pedido.getItens() != null) {
                for (ItemPedido item : pedido.getItens()) {
                    var produto = item.getProduto();
                    produto.setEstoque(produto.getEstoque() + item.getQuantidade());
                }
            }
        }

        Pedido pedidoAtualizado = pedidoRepository.save(pedido);

        // Notifica o frontend também sobre a mudança de status (opcional, mas muito bom)
        if (pedidoAtualizado.getConfeiteiro() != null) {
            String destino = "/topico/confeiteiro/" + pedidoAtualizado.getConfeiteiro().getId() + "/pedidos";
            messagingTemplate.convertAndSend(destino, pedidoAtualizado);
        }

        return pedidoAtualizado;
    }
}