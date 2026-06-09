package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.dto.KitRequestDTO;
import com.app.confeitaria.docelivery.dto.ProdutoDTO;
import com.app.confeitaria.docelivery.model.entity.*;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import com.app.confeitaria.docelivery.model.repository.CategoriaRepository;
import com.app.confeitaria.docelivery.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoService produtoService;

    // 1. CRIAR PRODUTO NORMAL
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestPart("produto") ProdutoDTO dto,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem,
            @RequestParam("confeiteiroId") Long confeiteiroId) {
        try {
            Usuario confeiteiro = usuarioRepository.findById(confeiteiroId)
                    .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado"));

            Produto produto = new Produto();
            produto.setNome(dto.nome());
            produto.setDescricao(dto.descricao());
            produto.setPreco(dto.preco());
            produto.setEstoque(dto.estoque());
            produto.setConfeiteiro(confeiteiro);

            if (dto.categoriaId() != null) {
                Categoria categoria = categoriaRepository.findById(dto.categoriaId())
                        .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
                produto.setCategoria(categoria);
            }

            if (imagem != null && !imagem.isEmpty()) {
                produto.setImagemUrl(produtoService.salvarFoto(imagem));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(produtoRepository.save(produto));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Erro ao salvar produto: " + e.getMessage());
        }
    }

    // 2. ATUALIZAR PRODUTO NORMAL
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestPart("produto") ProdutoDTO dto,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem) {
        try {
            Produto produtoExistente = produtoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

            produtoExistente.setNome(dto.nome());
            produtoExistente.setDescricao(dto.descricao());
            produtoExistente.setPreco(dto.preco());
            produtoExistente.setEstoque(dto.estoque());

            if (dto.categoriaId() != null) {
                Categoria categoria = categoriaRepository.findById(dto.categoriaId())
                        .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
                produtoExistente.setCategoria(categoria);
            }

            if (imagem != null && !imagem.isEmpty()) {
                produtoExistente.setImagemUrl(produtoService.salvarFoto(imagem));
            }

            return ResponseEntity.ok(produtoRepository.save(produtoExistente));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar: " + e.getMessage());
        }
    }

    // 3. BUSCAR POR LOJA / CONFEITEIRO
    @GetMapping("/store/{id}")
    public ResponseEntity<?> getByStore(@PathVariable Long id) {
        List<Produto> produtos = produtoRepository.findByConfeiteiroId(id);
        return ResponseEntity.ok(produtos);
    }

    // 4. DELETAR PRODUTO OU KIT
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return produtoRepository.findById(id).map(p -> {
            produtoRepository.delete(p);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // 5. LISTAR KITS DO CONFEITEIRO
    @GetMapping("/kit/confeiteiro/{id}")
    public ResponseEntity<?> getKitsByConfeiteiro(@PathVariable Long id) {
        try {
            List<Produto> kits = produtoRepository.findByConfeiteiroId(id);
            return ResponseEntity.ok(kits);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar kits: " + e.getMessage());
        }
    }

    // 6. ROTA AJUSTADA: CADASTRAR KIT COM IMAGEM EM UMA ÚNICA REQUISIÇÃO
    @PostMapping(value = "/kit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> criarKit(
            @RequestPart("kit") KitRequestDTO kitDTO,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem) {
        try {
            // 1. Chama o service que monta toda a estrutura do Kit e seus itens
            Produto kit = produtoService.cadastrarKit(kitDTO);

            // 2. Se o usuário enviou uma imagem na tela do React, salva e associa ao Kit
            if (imagem != null && !imagem.isEmpty()) {
                String nomeImagem = produtoService.salvarFoto(imagem);
                kit.setImagemUrl(nomeImagem); // 🟢 Vincula o nome da imagem salva

                // Salva novamente para persistir com a imagem inclusa
                kit = produtoRepository.save(kit);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(kit);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro interno ao processar o kit: " + e.getMessage()));
        }
    }
    // 7. VITRINE DO CLIENTE: Lista todos os produtos e kits cadastrados no sistema
    @GetMapping
    public ResponseEntity<List<Produto>> listarVitrineGeralCliente() {
        // Busca tudo. Idealmente, no futuro você pode criar um método no Repository
        // como 'findByCodStatusTrue' para trazer só os produtos ativos/disponíveis.
        List<Produto> produtos = produtoRepository.findAll();
        return ResponseEntity.ok(produtos);
    }
}