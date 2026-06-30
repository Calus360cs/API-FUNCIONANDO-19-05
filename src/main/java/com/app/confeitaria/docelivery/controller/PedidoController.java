package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.dto.ItemPedidoDTO;
import com.app.confeitaria.docelivery.dto.PedidoDTO;
import com.app.confeitaria.docelivery.model.entity.*;
import com.app.confeitaria.docelivery.model.enums.StatusPedido;
import com.app.confeitaria.docelivery.model.repository.ClienteRepository;
import com.app.confeitaria.docelivery.model.repository.LojaRepository;
import com.app.confeitaria.docelivery.service.MercadoPagoService;
import com.app.confeitaria.docelivery.service.MercadoPagoService.PagamentoResultado;
import com.app.confeitaria.docelivery.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final Logger log = LoggerFactory.getLogger(PedidoController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private LojaRepository lojaRepository;

    /**
     * CLIENTE: Realiza um novo pedido e processa o pagamento.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CheckoutRequest request) {
        try {
            log.info("POST /api/pedidos recebido - clienteId: {}, lojaId: {}, total: {}",
                    request.getClienteId(), request.getLojaId(), request.getTotal());

            // Monta a entidade Pedido a partir do request
            Pedido pedido = montarPedido(request);

            if (pedido == null) {
                return ResponseEntity.badRequest().body("Dados do pedido ausentes ou inválidos.");
            }
            if (pedido.getValorPedido() == null || pedido.getValorPedido() == 0.0) {
                return ResponseEntity.badRequest()
                        .body("Pedido inválido: valor zerado. Verifique os itens.");
            }

            // Resolve método de pagamento
            String metodoPagamento = resolverMetodoPagamento(request.getFormaPagamento());
            String tokenCartao = request.getTokenCartao();

            // Resolve e-mail do cliente para o Mercado Pago
            String emailCliente = resolverEmail(request.getEmailCliente(), pedido);

            log.info("Chamando MP - Valor: R$ {} | Método: {} | Email: {}",
                    pedido.getValorPedido(), metodoPagamento, emailCliente);

            // Chama o Mercado Pago
            PagamentoResultado resultado = mercadoPagoService.criarPagamentoComId(
                    BigDecimal.valueOf(pedido.getValorPedido()),
                    tokenCartao,
                    emailCliente,
                    metodoPagamento
            );

            // 🟢 TRATAMENTO ALINHADO: Intercepta os novos mapeamentos de erro do Service
            if (resultado.status() != null && resultado.status().startsWith("ERRO")) {
                log.warn("⚠️ Checkout interrompido devido a erro no gateway de pagamento: {}", resultado.status());

                if ("ERRO_API_MERCADO_PAGO".equals(resultado.status())) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body("O Mercado Pago recusou os dados enviados. Verifique o console do back-end para detalhes.");
                }

                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Falha de comunicação ou autenticação com o Mercado Pago. Tente novamente.");
            }

            // Salva o ID da transação (essencial para o webhook)
            if (resultado.transactionId() != null) {
                pedido.setMercadoPagoTransactionId(resultado.transactionId());
            }

            // Define status baseado no retorno do MP
            if ("approved".equalsIgnoreCase(resultado.status())) {
                pedido.setStatus(StatusPedido.PREPARANDO);
            } else {
                // PIX fica pending até o cliente pagar — webhook atualiza depois
                pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
            }

            Pedido novoPedido = pedidoService.realizarPedido(pedido);
            log.info("Pedido #{} criado com sucesso. Status MP: {}", novoPedido.getId(), resultado.status());

            return ResponseEntity.status(HttpStatus.CREATED).body(novoPedido);

        } catch (Exception e) {
            log.error("Erro ao processar checkout: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar checkout: " + e.getMessage());
        }
    }

    // ─── Métodos auxiliares privados ────────────────────────────────────────────

    private Pedido montarPedido(CheckoutRequest req) {
        final Long clienteId;
        if (req.getClienteId() != null) {
            clienteId = req.getClienteId();
        } else if (req.getCliente() != null && req.getCliente().getId() != null) {
            clienteId = req.getCliente().getId();
        } else {
            throw new RuntimeException("clienteId é obrigatório.");
        }

        final Long lojaId;
        if (req.getLojaId() != null) {
            lojaId = req.getLojaId();
        } else if (req.getConfeiteiro() != null && req.getConfeiteiro().getId() != null) {
            lojaId = req.getConfeiteiro().getId();
        } else {
            lojaId = null;
        }

        Pedido pedido = new Pedido();

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado: " + clienteId));
        pedido.setCliente(cliente);

        if (lojaId != null) {
            lojaRepository.findById(lojaId).ifPresentOrElse(
                    pedido::setLoja,
                    () -> {
                        Loja ref = new Loja();
                        ref.setId(lojaId);
                        pedido.setLoja(ref);
                    }
            );
        }

        if (req.getEnderecoEntrega() != null) {
            cliente.setEndereco(req.getEnderecoEntrega());
        }

        if (req.getTotal() != null) {
            pedido.setValorPedido(req.getTotal().doubleValue());
        }

        List<ItemPedido> itens = new ArrayList<>();

        if (req.getItens() != null && !req.getItens().isEmpty()) {
            for (ItemPedidoDTO itemDto : req.getItens()) {
                Long produtoId = itemDto.produtoId();
                if (produtoId == null) continue;

                ItemPedido item = new ItemPedido();
                item.setPedido(pedido);
                Produto produto = new Produto();
                produto.setId(produtoId);
                item.setProduto(produto);
                item.setQuantidade(itemDto.quantidade() != null ? itemDto.quantidade() : 1);
                if (itemDto.precoUnitario() != null) {
                    item.setPrecoUnitario(itemDto.precoUnitario().doubleValue());
                }
                itens.add(item);
            }
        }

        if (itens.isEmpty() && req.getItensRaw() != null && !req.getItensRaw().isEmpty()) {
            for (Map<String, Object> itemMap : req.getItensRaw()) {
                ItemPedido item = new ItemPedido();
                item.setPedido(pedido);

                Long produtoId = null;
                Object produtoObj = itemMap.get("produto");
                if (produtoObj instanceof Map) {
                    Object idObj = ((Map<?, ?>) produtoObj).get("id");
                    if (idObj != null) produtoId = Long.parseLong(idObj.toString());
                }
                if (produtoId == null && itemMap.get("produtoId") != null) {
                    produtoId = Long.parseLong(itemMap.get("produtoId").toString());
                }

                if (produtoId == null) continue;
                Produto produto = new Produto();
                produto.setId(produtoId);
                item.setProduto(produto);

                Object qtd = itemMap.get("quantidade");
                item.setQuantidade(qtd != null ? Integer.parseInt(qtd.toString()) : 1);
                itens.add(item);
            }
        }

        pedido.setItens(itens);
        return pedido;
    }

    private String resolverMetodoPagamento(String formaPagamento) {
        if (formaPagamento == null) return "pix";
        switch (formaPagamento.toUpperCase()) {
            case "CREDIT":      return "credit_card";
            case "CREDIT_CARD": return "credit_card";
            case "DEBIT":       return "debit_card";
            case "DEBIT_CARD":  return "debit_card";
            case "PIX":
            default:            return "pix";
        }
    }

    private String resolverEmail(String emailDoRequest, Pedido pedido) {
        if (emailDoRequest != null && !emailDoRequest.isBlank()) {
            return emailDoRequest;
        }
        if (pedido.getCliente() != null
                && pedido.getCliente().getEmail() != null
                && !pedido.getCliente().getEmail().isBlank()) {
            return pedido.getCliente().getEmail();
        }
        if (pedido.getCliente() != null && pedido.getCliente().getId() != null) {
            return clienteRepository.findById(pedido.getCliente().getId())
                    .map(c -> (c.getEmail() != null && !c.getEmail().isBlank())
                            ? c.getEmail() : "test_user_123456@testuser.com")
                    .orElse("test_user_123456@testuser.com");
        }
        return "test_user_123456@testuser.com";
    }

    // ─── Endpoints restantes ────────────────────────────────────────────────────

    @GetMapping("/confeiteiro/{id}/fila")
    public ResponseEntity<List<PedidoDTO>> getFilaTrabalho(@PathVariable Long id) {
        List<String> statusAtivos = Arrays.asList("NOVO", "PREPARANDO", "PAGO");
        List<Pedido> pedidos = pedidoService.buscarFilaConfeiteiro(id, statusAtivos);
        List<PedidoDTO> dtos = pedidos.stream()
                .map(pedidoService::converterParaDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> getById(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.buscarPorId(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Pedido> atualizarStatus(
            @PathVariable Long id,
            @RequestParam String novoStatus) {
        return ResponseEntity.ok(pedidoService.atualizarStatusViaString(id, novoStatus));
    }

    @PostMapping("/confeiteiro/{confeiteiroId}")
    public ResponseEntity<Pedido> criarPedidoManualmente(
            @PathVariable Long confeiteiroId,
            @RequestBody Pedido pedido) {
        Confeiteiro c = new Confeiteiro();
        c.setId(confeiteiroId);
        pedido.setConfeiteiro(c);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.realizarPedido(pedido));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PedidoDTO>> getHistoricoCliente(@PathVariable Long clienteId) {
        List<Pedido> pedidos = pedidoService.buscarPedidosPorCliente(clienteId);
        List<PedidoDTO> dtos = pedidos.stream()
                .map(pedidoService::converterParaDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // ─── Classe de request unificada e IdRef mantidos intactos ───────────────────

    public static class CheckoutRequest {
        private Long clienteId;
        private Long lojaId;
        private BigDecimal total;
        private List<ItemPedidoDTO> itens;

        @com.fasterxml.jackson.annotation.JsonAlias("itens")
        private List<Map<String, Object>> itensRaw;

        private IdRef cliente;
        private IdRef confeiteiro;
        private Double valorPedido;
        private String enderecoEntrega;
        private String formaPagamento;
        private String tokenCartao;
        private String emailCliente;

        public Long getClienteId() { return clienteId; }
        public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
        public Long getLojaId() { return lojaId; }
        public void setLojaId(Long lojaId) { this.lojaId = lojaId; }
        public BigDecimal getTotal() {
            if (total != null) return total;
            if (valorPedido != null) return BigDecimal.valueOf(valorPedido);
            return null;
        }
        public void setTotal(BigDecimal total) { this.total = total; }
        public void setValorPedido(Double valorPedido) { this.valorPedido = valorPedido; }
        public List<ItemPedidoDTO> getItens() { return itens; }
        public void setItens(List<ItemPedidoDTO> itens) { this.itens = itens; }
        public IdRef getCliente() { return cliente; }
        public void setCliente(IdRef cliente) { this.cliente = cliente; }
        public IdRef getConfeiteiro() { return confeiteiro; }
        public void setConfeiteiro(IdRef confeiteiro) { this.confeiteiro = confeiteiro; }
        public List<Map<String, Object>> getItensRaw() { return itensRaw; }
        public void setItensRaw(List<Map<String, Object>> itensRaw) { this.itensRaw = itensRaw; }
        public String getEnderecoEntrega() { return enderecoEntrega; }
        public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }
        public String getFormaPagamento() { return formaPagamento; }
        public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }
        public String getTokenCartao() { return tokenCartao; }
        public void setTokenCartao(String tokenCartao) { this.tokenCartao = tokenCartao; }
        public String getEmailCliente() { return emailCliente; }
        public void setEmailCliente(String emailCliente) { this.emailCliente = emailCliente; }
    }

    public static class IdRef {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}