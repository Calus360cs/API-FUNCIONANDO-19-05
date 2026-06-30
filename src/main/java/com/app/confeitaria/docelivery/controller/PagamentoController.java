package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.model.entity.Pedido;
import com.app.confeitaria.docelivery.model.entity.Cliente;
import com.app.confeitaria.docelivery.model.entity.Loja;
import com.app.confeitaria.docelivery.model.repository.PedidoRepository;
import com.app.confeitaria.docelivery.model.repository.ClienteRepository;
import com.app.confeitaria.docelivery.model.repository.LojaRepository;
import com.app.confeitaria.docelivery.model.enums.StatusPedido;
import com.app.confeitaria.docelivery.dto.PedidoDTO;
import com.app.confeitaria.docelivery.service.MercadoPagoService;
import com.app.confeitaria.docelivery.service.MercadoPagoService.PagamentoResultado;
import com.app.confeitaria.docelivery.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {

    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private PedidoService pedidoService;

    /**
     * Cria um pedido no banco e gera o pagamento via PIX no Mercado Pago.
     * Recebe um PedidoDTO com clienteId, lojaId e total.
     */
    @PostMapping("/processar")
    public ResponseEntity<?> criarPedidoEGerarPix(@RequestBody PedidoDTO dto) {

        // 1. Valida os IDs obrigatórios
        if (dto.clienteId() == null || dto.lojaId() == null || dto.total() == null) {
            return ResponseEntity.badRequest().body("Campos obrigatórios ausentes: clienteId, lojaId ou total.");
        }

        // 2. Busca as entidades do banco
        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado com ID: " + dto.clienteId()));

        Loja loja = lojaRepository.findById(dto.lojaId())
                .orElseThrow(() -> new RuntimeException("Loja não encontrada com ID: " + dto.lojaId()));

        // 3. Monta o pedido com status AGUARDANDO_PAGAMENTO antes de chamar o MP
        Pedido novoPedido = new Pedido();
        novoPedido.setCliente(cliente);
        novoPedido.setLoja(loja);
        novoPedido.setValorPedido(dto.total().doubleValue());
        novoPedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        novoPedido.setDataHoraPedido(java.time.LocalDateTime.now());
        novoPedido.setCodStatus(true);
        novoPedido.setNumeroPedido("DOCE-" + java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase());

        // Salva antes de chamar o MP para garantir rastro do pedido em caso de falha
        Pedido pedidoSalvo = pedidoRepository.save(novoPedido);

        // 4. Garante e-mail válido para o Mercado Pago (obrigatório no Sandbox)
        String emailComprador = (cliente.getEmail() != null && !cliente.getEmail().isBlank())
                ? cliente.getEmail()
                : "test_user_123456@testuser.com";

        log.info("Iniciando pagamento PIX - Pedido #{} | Valor: R$ {} | Comprador: {}",
                pedidoSalvo.getId(), dto.total(), emailComprador);

        // 5. Chama o Mercado Pago e obtém TANTO o ID da transação quanto o status
        PagamentoResultado resultado;
        try {
            resultado = mercadoPagoService.criarPagamentoComId(
                    dto.total(),
                    null,           // PIX não usa token de cartão
                    emailComprador,
                    "pix"
            );
        } catch (Exception e) {
            log.error("Falha ao chamar SDK do Mercado Pago para o pedido #{}: {}", pedidoSalvo.getId(), e.getMessage(), e);
            // Mantém o pedido salvo mas informa o erro ao front-end
            Map<String, Object> erroResposta = new HashMap<>();
            erroResposta.put("pedidoId", pedidoSalvo.getId());
            erroResposta.put("statusPagamento", "ERRO");
            erroResposta.put("mensagem", "Falha ao conectar com o Mercado Pago. Tente novamente.");
            return ResponseEntity.internalServerError().body(erroResposta);
        }

        // 6. Salva o ID da transação do MP no pedido (ESSENCIAL para o webhook funcionar)
        if (resultado.transactionId() != null) {
            pedidoSalvo.setMercadoPagoTransactionId(resultado.transactionId());
        }

        // 7. Atualiza o status com base no retorno síncrono do MP
        String statusMP = resultado.status();
        if ("approved".equalsIgnoreCase(statusMP)) {
            pedidoSalvo.setStatus(StatusPedido.PAGO);
            log.info("Pagamento aprovado sincronamente para o pedido #{}", pedidoSalvo.getId());
        } else if ("pending".equalsIgnoreCase(statusMP) || "in_process".equalsIgnoreCase(statusMP)) {
            pedidoSalvo.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
            log.info("Pagamento PIX pendente para o pedido #{} - aguardando confirmação do webhook", pedidoSalvo.getId());
        } else {
            // rejected, cancelled, ERRO ou outro
            pedidoSalvo.setStatus(StatusPedido.CANCELADO);
            log.warn("Pagamento rejeitado/erro para o pedido #{} - Status MP: {}", pedidoSalvo.getId(), statusMP);
        }

        pedidoRepository.save(pedidoSalvo);

        // 8. Resposta ao front-end
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("pedidoId", pedidoSalvo.getId());
        resposta.put("numeroPedido", pedidoSalvo.getNumeroPedido());
        resposta.put("statusPagamento", statusMP);
        resposta.put("transactionId", resultado.transactionId());

        return ResponseEntity.ok(resposta);
    }

    /**
     * Webhook público — recebe notificações de pagamento do Mercado Pago.
     * IMPORTANTE: Para produção, implemente a validação da assinatura (x-signature)
     * conforme documentação: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> receberNotificacaoMercadoPago(@RequestBody Map<String, Object> payload) {

        log.info("Webhook MP recebido: {}", payload);

        // O MP envia action "payment.updated" ou "payment.created"
        if (!payload.containsKey("data")) {
            log.warn("Webhook recebido sem campo 'data', ignorando.");
            return ResponseEntity.ok().build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String idTransacaoMP = data.get("id").toString();

            log.info("Consultando transação MP ID: {}", idTransacaoMP);

            // Consulta o status atualizado diretamente no MP (não confiar só no payload)
            com.mercadopago.client.payment.PaymentClient paymentClient = new com.mercadopago.client.payment.PaymentClient();
            com.mercadopago.resources.payment.Payment payment = paymentClient.get(Long.parseLong(idTransacaoMP));

            String statusMP = payment.getStatus();
            log.info("Webhook - Transação {}: Status = {}", idTransacaoMP, statusMP);

            if ("approved".equalsIgnoreCase(statusMP)) {
                Pedido pedido = pedidoRepository.findByMercadoPagoTransactionId(idTransacaoMP)
                        .orElse(null);

                if (pedido == null) {
                    log.warn("Webhook: Pedido não encontrado para transação MP ID: {}. Verifique se mercadoPagoTransactionId foi salvo corretamente.", idTransacaoMP);
                    return ResponseEntity.ok().build();
                }

                // Atualiza para PAGO e em seguida para NOVO (fila do confeiteiro)
                pedido.setStatus(StatusPedido.PAGO);
                pedidoRepository.save(pedido);

                // Dispara a atualização de status via websocket para o confeiteiro
                pedidoService.atualizarStatusViaString(pedido.getId(), "NOVO");

                log.info("Webhook SUCESSO: Pedido #{} confirmado como PAGO e enviado para fila.", pedido.getId());
            }

        } catch (com.mercadopago.exceptions.MPException | com.mercadopago.exceptions.MPApiException e) {
            log.error("Erro ao consultar transação no Webhook MP: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro inesperado no processamento do Webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
