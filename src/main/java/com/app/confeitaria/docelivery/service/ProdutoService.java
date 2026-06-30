package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.KitItemRequestDTO;
import com.app.confeitaria.docelivery.dto.KitRequestDTO;
import com.app.confeitaria.docelivery.dto.ProdutoDTO;
import com.app.confeitaria.docelivery.model.entity.Categoria;
import com.app.confeitaria.docelivery.model.entity.KitItem;
import com.app.confeitaria.docelivery.model.entity.Produto;
import com.app.confeitaria.docelivery.model.entity.Usuario;
import com.app.confeitaria.docelivery.model.repository.CategoriaRepository;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional
    public Produto criarProdutoComFoto(ProdutoDTO dto, MultipartFile file) {
        Produto produto = new Produto();
        produto.setNome(dto.nome());
        produto.setDescricao(dto.descricao());
        produto.setPreco(dto.preco());
        produto.setEstoque(dto.estoque());
        produto.setCodStatus(true);

        produto.setCategoriaId(dto.categoriaId());

        if (file != null && !file.isEmpty()) {
            String nomeSalvo = salvarFoto(file);
            produto.setImagemUrl(nomeSalvo);
        }

        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto cadastrarKit(KitRequestDTO dto) {
        if (dto.getConfeiteiroId() == null) {
            throw new IllegalArgumentException("O ID do confeiteiro não foi enviado.");
        }

        Usuario confeiteiro = usuarioRepository.findById(dto.getConfeiteiroId())
                .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado."));

        Produto kit = new Produto();
        kit.setNome(dto.getNome());
        kit.setDescricao(dto.getDescricao());
        kit.setPreco(dto.getPreco());
        kit.setConfeiteiro(confeiteiro);
        kit.setEstoque(9999);
        kit.setCodStatus(true);

        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada."));
            kit.setCategoria(categoria);
        } else {
            throw new IllegalArgumentException("O campo categoriaId é obrigatório.");
        }

        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            for (KitItemRequestDTO itemDto : dto.getItens()) {
                Produto produtoExistente = produtoRepository.findById(itemDto.getProdutoId())
                        .orElseThrow(() -> new RuntimeException("O produto não existe."));

                KitItem kitItem = new KitItem();
                kitItem.setKit(kit);
                kitItem.setProduto(produtoExistente);
                kitItem.setQuantidade(itemDto.getQuantidade());

                kit.getItens().add(kitItem);
            }
        } else {
            throw new RuntimeException("Não é possível criar um kit sem produtos.");
        }

        return produtoRepository.save(kit);
    }

    @Transactional
    public Produto alterarProduto(Long id, ProdutoDTO dto, MultipartFile file) {
        Produto produtoExistente = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        produtoExistente.setNome(dto.nome());
        produtoExistente.setDescricao(dto.descricao());
        produtoExistente.setPreco(dto.preco());
        produtoExistente.setEstoque(dto.estoque());
        produtoExistente.setCategoriaId(dto.categoriaId());

        if (file != null && !file.isEmpty()) {
            String nomeSalvo = salvarFoto(file);
            produtoExistente.setImagemUrl(nomeSalvo);
        }

        return produtoRepository.save(produtoExistente);
    }

    // CORREÇÃO DEFINITIVA DO INVERSOR DE STATUS DE ATIVAÇÃO
    @Transactional
    public void desativarProduto(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        boolean statusAtual = produto.getCodStatus() != null ? produto.getCodStatus() : false;
        produto.setCodStatus(!statusAtual);

        // Salva e esvazia o buffer da transação imediatamente para liberar o banco de dados
        produtoRepository.saveAndFlush(produto);
    }

    @Transactional
    public void excluirFisicamente(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));
        try {
            produtoRepository.delete(produto);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Não é possível excluir este produto pois possui pedidos vinculados.");
        }
    }

    public String salvarFoto(MultipartFile file) {
        String nomeArquivo = System.currentTimeMillis() + "_" +
                file.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        try {
            Path pastaDestino = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(pastaDestino)) {
                Files.createDirectories(pastaDestino);
            }
            Files.copy(file.getInputStream(), pastaDestino.resolve(nomeArquivo));
            return nomeArquivo;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar a imagem no servidor", e);
        }
    }

    @Transactional
    public void excluirKitFisicamente(Long id) {
        Produto kit = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kit não encontrado."));
        try {
            if (kit.getItens() != null && !kit.getItens().isEmpty()) {
                kit.getItens().clear();
                produtoRepository.saveAndFlush(kit);
            }
            produtoRepository.delete(kit);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Não é possível excluir este kit.");
        }
    }
}