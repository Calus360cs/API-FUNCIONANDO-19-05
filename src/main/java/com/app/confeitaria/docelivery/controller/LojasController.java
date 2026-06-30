package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.dto.ConfeiteiroDTO;
import com.app.confeitaria.docelivery.model.entity.Produto;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.service.ConfeiteiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class LojasController {

    @Autowired
    private ConfeiteiroService confeiteiroService; // Injeção do Service adicionada

    @Autowired
    private ProdutoRepository produtoRepository;

    // GET /api/stores - Retorna todas as lojas convertidas em DTO (Padroniza as imagens)
    @GetMapping
    public ResponseEntity<List<ConfeiteiroDTO>> listarLojas() {
        List<ConfeiteiroDTO> lojas = confeiteiroService.listarTodasAsLojas();
        return ResponseEntity.ok(lojas);
    }

    // GET /api/stores/{id} - Retorna uma loja específica em DTO
    @GetMapping("/{id}")
    public ResponseEntity<ConfeiteiroDTO> buscarLojaPorId(@PathVariable Long id) {
        try {
            ConfeiteiroDTO dto = confeiteiroService.buscarConfeiteiroPorId(id);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // GET /api/stores/{id}/menu - Retorna o cardápio da loja
    @GetMapping("/{id}/menu")
    public ResponseEntity<List<Produto>> buscarMenuDaLoja(@PathVariable Long id) {
        List<Produto> menu = produtoRepository.findKitsByConfeiteiroId(id);
        return ResponseEntity.ok(menu);
    }
}