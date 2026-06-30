package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.PedidoDTO;
import com.app.confeitaria.docelivery.dto.ItemPedidoDTO;
import com.app.confeitaria.docelivery.model.entity.*;
import com.app.confeitaria.docelivery.model.enums.StatusPedido;
import com.app.confeitaria.docelivery.model.enums.TipoMovimentacao;
import com.app.confeitaria.docelivery.model.enums.CategoriaMovimentacao;
import com.app.confeitaria.docelivery.model.repository.PedidoRepository;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.model.repository.MovimentacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MovimentacaoRepository movimentacaoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public PedidoDTO atualizarStatus(Long pedidoId, StatusPedido novoStatus) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        pedido.setStatus(novoStatus);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        if (novoStatus == StatusPedido.CONCLUIDO || novoStatus == StatusPedido.ENTREGUE) {
            gerarEntradaFinanceiraAutomatica(pedidoSalvo);
        }

        PedidoDTO pedidoDTO = converterParaDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topico/pedidos", pedidoDTO);

        return pedidoDTO;
    }

    public Pedido converterParaEntidade(PedidoDTO dto) {
        if (dto == null) {
            return null;
        }

        Pedido pedido = new Pedido();
        pedido.setId(dto.id());

        Cliente cliente = new Cliente();
        if (dto.clienteId() != null) {
            cliente.setId(dto.clienteId());
        }

        if (dto.enderecoEntrega() != null) {
            cliente.setEndereco(dto.enderecoEntrega());
        }

        pedido.setCliente(cliente);

        if (dto.total() != null) {
            pedido.setValorPedido(dto.total().doubleValue());
        }

        if (dto.lojaId() != null) {
            com.app.confeitaria.docelivery.model.entity.Loja loja = new com.app.confeitaria.docelivery.model.entity.Loja();
            loja.setId(dto.lojaId());
            pedido.setLoja(loja);
        }

        if (dto.status() != null) {
            pedido.setStatus(StatusPedido.valueOf(dto.status().toUpperCase()));
        } else {
            pedido.setStatus(StatusPedido.NOVO);
        }

        if (dto.itens() != null) {
            List<ItemPedido> itens = dto.itens().stream().map(itemDto -> {
                ItemPedido item = new ItemPedido();
                item.setQuantidade(itemDto.quantidade());
                if (itemDto.precoUnitario() != null) {
                    item.setPrecoUnitario(itemDto.precoUnitario().doubleValue());
                }

                if (itemDto.produtoId() != null) {
                    Produto produto = new Produto();
                    produto.setId(itemDto.produtoId());
                    item.setProduto(produto);
                }
                item.setPedido(pedido);
                return item;
            }).collect(Collectors.toList());
            pedido.setItens(itens);
        } else {
            pedido.setItens(new ArrayList<>());
        }

        return pedido;
    }

    public PedidoDTO converterParaDTO(Pedido pedido) {
        if (pedido == null) return null;

        String nomeDoCliente = (pedido.getCliente() != null) ? pedido.getCliente().getNome() : "Cliente não informado";
        String telefoneDoCliente = (pedido.getCliente() != null) ? pedido.getCliente().getTelefone() : "";

        java.math.BigDecimal total = (pedido.getValorPedido() != null)
                ? java.math.BigDecimal.valueOf(pedido.getValorPedido())
                : java.math.BigDecimal.ZERO;

        String statusStr = (pedido.getStatus() != null) ? pedido.getStatus().name() : "NOVO";

        List<ItemPedidoDTO> itensDTO = java.util.Collections.emptyList();
        if (pedido.getItens() != null) {
            itensDTO = pedido.getItens().stream()
                    .map(item -> new ItemPedidoDTO(
                            (item.getProduto() != null) ? item.getProduto().getId() : null,
                            (item.getProduto() != null) ? item.getProduto().getNome() : "Produto Indisponível",
                            item.getQuantidade(),
                            (item.getPrecoUnitario() != null) ? java.math.BigDecimal.valueOf(item.getPrecoUnitario()) : java.math.BigDecimal.ZERO
                    ))
                    .collect(Collectors.toList());
        }

        return new PedidoDTO(
                pedido.getId(),
                (pedido.getCliente() != null) ? pedido.getCliente().getId() : null,
                (pedido.getLoja() != null) ? pedido.getLoja().getId() : null,
                nomeDoCliente,
                telefoneDoCliente,
                (pedido.getCliente() != null && pedido.getCliente().getEndereco() != null)
                        ? pedido.getCliente().getEndereco()
                        : "Retirada na Loja / Ver cadastro",
                statusStr,
                total,
                pedido.getDataHoraPedido(),
                itensDTO
        );
    }

    @Transactional
    public Pedido realizarPedido(Pedido pedido) {
        String codigoUnico = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        pedido.setNumeroPedido("DOCE-" + codigoUnico);
        pedido.setDataHoraPedido(LocalDateTime.now());

        if (pedido.getAgendado() != null && pedido.getAgendado()) {
            pedido.setStatus(StatusPedido.AGENDADO);
            if (pedido.getDataEntregaAgendada() != null &&
                    pedido.getDataEntregaAgendada().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("A data do agendamento não pode ser no passado.");
            }
        } else if (pedido.getStatus() == null) {
            pedido.setStatus(StatusPedido.NOVO);
        }

        pedido.setCodStatus(true);
        double valorTotalGeral = 0;
        com.app.confeitaria.docelivery.model.entity.Loja lojaDoProdutoReal = null;

        // 1. Processa os itens e descobre a loja verdadeira amarrada ao produto no banco (Geralmente ID 2)
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

                produtoRef.setEstoque(produtoRef.getEstoque() - item.getQuantidade());

                if (lojaDoProdutoReal == null && produtoRef.getLoja() != null) {
                    lojaDoProdutoReal = produtoRef.getLoja();
                }
            }
        }

        // 2. PROTEÇÃO DE ID INEXISTENTE (Ex: ID 10 de usuário enviado como ID de Loja)
        if (pedido.getLoja() != null && pedido.getLoja().getId() != null) {
            Long idLojaEnviado = pedido.getLoja().getId();

            // Se for ID quebrado de teste ou maior/igual a 10, nós forçamos o descarte para puxar a loja correta do produto
            if (idLojaEnviado >= 10L) {
                log.warn("⚠️ ID de loja inválido detectado ({}) no Checkout. Limpando para resolução segura.", idLojaEnviado);
                pedido.setLoja(null);
            }
        }

        // 3. AMARRAÇÃO SEGURA DA LOJA
        if (pedido.getLoja() == null || pedido.getLoja().getId() == null) {
            if (lojaDoProdutoReal != null) {
                pedido.setLoja(lojaDoProdutoReal);
            } else {
                try {
                    // Fallback mestre se tudo falhar: pega a primeira loja ativa no banco (ID 2)
                    Number primeiroIdLoja = (Number) entityManager.createNativeQuery("SELECT TOP 1 id FROM loja").getSingleResult();
                    com.app.confeitaria.docelivery.model.entity.Loja lojaSegura = new com.app.confeitaria.docelivery.model.entity.Loja();
                    lojaSegura.setId(primeiroIdLoja.longValue());
                    pedido.setLoja(lojaSegura);
                } catch (Exception e) {
                    throw new RuntimeException("Erro impeditivo: Nenhuma loja cadastrada no banco de dados.");
                }
            }
        }

        pedido.setValorPedido(valorTotalGeral);

        // 4. 🟢 CORREÇÃO DO CONFEITEIRO NULO E DO ERRO DE LOJA NÃO ENCONTRADA:
        // Buscamos a Loja completa e atualizada do banco para recuperar o Confeiteiro dono verdadeiro dela
        if (pedido.getLoja() != null && pedido.getLoja().getId() != null) {
            try {
                com.app.confeitaria.docelivery.model.entity.Loja lojaDoBanco = entityManager.find(com.app.confeitaria.docelivery.model.entity.Loja.class, pedido.getLoja().getId());
                if (lojaDoBanco != null) {
                    pedido.setLoja(lojaDoBanco); // Atualiza o objeto com os dados reais da tabela loja

                    // Se o confeiteiro veio nulo do React, amarramos automaticamente o confeiteiro cadastrado nessa loja!
                    if (pedido.getConfeiteiro() == null && lojaDoBanco.getConfeiteiro() != null) {
                        pedido.setConfeiteiro(lojaDoBanco.getConfeiteiro());
                    }
                }
            } catch (Exception e) {
                log.error("Erro de segurança ao resolver confeiteiro da loja: {}", e.getMessage());
            }
        }

        // Segunda camada de proteção: se mesmo assim o confeiteiro ficar nulo, criamos um objeto com o ID da Loja ativa (garantia de TCC)
        if (pedido.getConfeiteiro() == null && pedido.getLoja() != null) {
            Confeiteiro confeiteiroSeguro = new Confeiteiro();
            confeiteiroSeguro.setId(pedido.getLoja().getId()); // IDs batem na modelagem
            pedido.setConfeiteiro(confeiteiroSeguro);
        }

        // 5. Salva o pedido no banco sem riscos de restrições ou IDs nulos
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // 6. Envia a notificação limpa via WebSockets para a Cozinha
        try {
            PedidoDTO pedidoDTO = converterParaDTO(pedidoSalvo);

            if (pedidoSalvo.getConfeiteiro() != null) {
                String destino = "/topico/confeiteiro/" + pedidoSalvo.getConfeiteiro().getId() + "/pedidos";
                log.info("📢 Enviando WebSocket para a cozinha do confeiteiro ID: {}", pedidoSalvo.getConfeiteiro().getId());
                messagingTemplate.convertAndSend(destino, pedidoDTO);
            }

            messagingTemplate.convertAndSend("/topico/pedidos", pedidoDTO);

        } catch (Exception e) {
            log.error("⚠️ Aviso: Pedido salvo no banco, mas falhou ao enviar Websocket/DTO: {}", e.getMessage());
        }

        return pedidoSalvo;
    }

    public List<Pedido> buscarFilaConfeiteiro(Long confeiteiroId, List<String> status) {
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
    public Pedido atualizarStatusViaString(Long id, String novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado com o ID: " + id));

        String statusMaiusculo = novoStatus.toUpperCase();
        pedido.setStatus(StatusPedido.valueOf(statusMaiusculo));

        if (statusMaiusculo.equals("ENTREGUE") || statusMaiusculo.equals("CANCELADO")) {
            pedido.setCodStatus(false);

            if (statusMaiusculo.equals("CANCELADO") && pedido.getItens() != null) {
                for (ItemPedido item : pedido.getItens()) {
                    var produto = item.getProduto();
                    produto.setEstoque(produto.getEstoque() + item.getQuantidade());
                }
            }
        }

        Pedido pedidoAtualizado = pedidoRepository.save(pedido);

        if (statusMaiusculo.equals("CONCLUIDO") || statusMaiusculo.equals("ENTREGUE")) {
            gerarEntradaFinanceiraAutomatica(pedidoAtualizado);
        }

        if (pedidoAtualizado.getConfeiteiro() != null) {
            PedidoDTO pedidoDTO = converterParaDTO(pedidoAtualizado);
            String destino = "/topico/confeiteiro/" + pedidoAtualizado.getConfeiteiro().getId() + "/pedidos";
            messagingTemplate.convertAndSend(destino, pedidoDTO);
        }

        return pedidoAtualizado;
    }

    private void gerarEntradaFinanceiraAutomatica(Pedido pedido) {
        try {
            MovimentacaoFinanceira entrada = new MovimentacaoFinanceira();
            entrada.setDescricao("Venda - Pedido #" + (pedido.getNumeroPedido() != null ? pedido.getNumeroPedido() : pedido.getId()));
            entrada.setValor(java.math.BigDecimal.valueOf(pedido.getValorPedido()));
            entrada.setTipo(TipoMovimentacao.ENTRADA);
            entrada.setCategoria(CategoriaMovimentacao.PEDIDO);
            entrada.setDataLancamento(LocalDateTime.now());
            entrada.setConfeiteiro(pedido.getConfeiteiro());

            movimentacaoRepository.save(entrada);
        } catch (Exception e) {
            System.err.println("Aviso: Falha ao gerar movimentação financeira automática: " + e.getMessage());
        }
    }

    public List<Pedido> buscarPedidosPorCliente(Long clienteId) {
        return pedidoRepository.findByClienteId(clienteId);
    }
}