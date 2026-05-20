package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.dto.PedidoDTO;
import com.app.confeitaria.docelivery.model.entity.Pedido;
import com.app.confeitaria.docelivery.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Controller responsável pelo gerenciamento de pedidos.
 * Localizado no pacote base para garantir que o Component Scan do Spring o encontre.
 */
@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5175"}) // Alinhado com seu CorsConfig
public class PedidoController {

    private final PedidoService pedidoService;

    // Injeção via construtor: Recomendado para garantir a imutabilidade e facilitar testes.
    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /**
     * CLIENTE: Realiza um novo pedido.
     * O status inicial é definido como 'NOVO' na regra de negócio do Service.
     */
    @PostMapping
    public ResponseEntity<Pedido> create(@RequestBody Pedido pedido) {
        Pedido novoPedido = pedidoService.realizarPedido(pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoPedido);
    }

    /**
     * CONFEITEIRO: Lista pedidos ativos (Fila de Trabalho).
     * Filtra por status específicos para a visualização do painel.
     */
    @GetMapping("/confeiteiro/{id}/fila")
    public ResponseEntity<List<Pedido>> getFilaTrabalho(@PathVariable Long id) {
        // CORRIGIDO: Mudamos para "NOVO" no lugar de "PENDENTE" para bater exatamente com as opções do seu Enum StatusPedido
        List<String> statusAtivos = Arrays.asList("NOVO", "PREPARANDO");
        List<Pedido> pedidos = pedidoService.buscarFilaConfeiteiro(id, statusAtivos);
        return ResponseEntity.ok(pedidos);
    }

    /**
     * Detalhes de um pedido específico por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pedido> getById(@PathVariable Long id) {
        Pedido pedido = pedidoService.buscarPorId(id);
        return ResponseEntity.ok(pedido);
    }

    /**
     * CONFEITEIRO: Atualiza o status do pedido (Ex: NOVO -> PREPARANDO).
     * Utiliza PATCH para atualizações parciais de estado.
     */
    @PatchMapping("/{id}/status")
    // CORRIGIDO: Mudamos o retorno de ResponseEntity<Pedido> para ResponseEntity<PedidoDTO>
    // e chamamos o método correto do Service que aceita a String: atualizarStatusViaString
    public ResponseEntity<Pedido> atualizarStatus(
            @PathVariable Long id,
            @RequestParam String novoStatus) {

        Pedido atualizado = pedidoService.atualizarStatusViaString(id, novoStatus);
        return ResponseEntity.ok(atualizado);
    }
}