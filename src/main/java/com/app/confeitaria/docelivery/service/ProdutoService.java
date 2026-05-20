package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.KitItemRequestDTO; // Ajuste o pacote se necessário
import com.app.confeitaria.docelivery.dto.KitRequestDTO;     // Ajuste o pacote se necessário
import com.app.confeitaria.docelivery.dto.ProdutoDTO;
import com.app.confeitaria.docelivery.model.entity.Categoria;
import com.app.confeitaria.docelivery.model.entity.KitItem;  // Sua entidade associativa
import com.app.confeitaria.docelivery.model.entity.Produto;
import com.app.confeitaria.docelivery.model.entity.Usuario;
import com.app.confeitaria.docelivery.model.repository.CategoriaRepository;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository; // Injetado para validar o dono do kit (loja/confeiteiro)

    @Autowired
    private CategoriaRepository categoriaRepository; // 🟢 Injeção correta

    @Value("${app.upload.dir}")
    private String uploadDir;

    // 1. SEU MÉTODO EXISTENTE (PRODUTO COMUM)
    public Produto criarProdutoComFoto(ProdutoDTO dto, MultipartFile file) {
        Produto produto = new Produto();
        // Mapear dados do DTO para a entidade...

        if (file != null && !file.isEmpty()) {
            String nomeSalvo = salvarFoto(file);
            produto.setImagemUrl(nomeSalvo);
        }

        return produtoRepository.save(produto);
    }

    // 2. 🟢 NOVO MÉTODO: CADASTRAR KIT COM PRODUTOS EXISTENTES
    @Transactional
    public Produto cadastrarKit(KitRequestDTO dto) {

        // 1️⃣ Validação do ID do Confeiteiro
        if (dto.getConfeiteiroId() == null) {
            throw new IllegalArgumentException("O ID do confeiteiro não foi enviado pelo front-end.");
        }

        Usuario confeiteiro = usuarioRepository.findById(dto.getConfeiteiroId())
                .orElseThrow(() -> new RuntimeException("Confeiteiro com ID " + dto.getConfeiteiroId() + " não foi encontrado."));

        // B) Instancia o Kit como um novo Produto principal
        Produto kit = new Produto();
        kit.setNome(dto.getNome());
        kit.setDescricao(dto.getDescricao());
        kit.setPreco(dto.getPreco());
        kit.setConfeiteiro(confeiteiro);
        kit.setEstoque(9999);

        // 🟢 ADICIONE ESTE BLOCO PARA SETAR A CATEGORIA VINDA DO DTO
        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria de ID " + dto.getCategoriaId() + " não encontrada."));
            kit.setCategoria(categoria);
        } else {
            throw new IllegalArgumentException("O campo categoriaId é obrigatório e não foi enviado no JSON.");
        }


        // C) Percorre os itens enviados pelo React
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            for (KitItemRequestDTO itemDto : dto.getItens()) {

                // 2️⃣ Validação Crítica do ID do Produto dentro da lista
                if (itemDto.getProdutoId() == null) {
                    throw new IllegalArgumentException("Um dos produtos adicionados ao kit está com o ID nulo/vazio.");
                }

                Produto produtoExistente = produtoRepository.findById(itemDto.getProdutoId())
                        .orElseThrow(() -> new RuntimeException("O produto de ID " + itemDto.getProdutoId() + " não existe no catálogo."));

                KitItem kitItem = new KitItem();
                kitItem.setKit(kit);
                kitItem.setProduto(produtoExistente);
                kitItem.setQuantidade(itemDto.getQuantidade());

                kit.getItens().add(kitItem);
            }
        } else {
            throw new RuntimeException("Não é possível criar um kit sem nenhum produto associado.");
        }

        return produtoRepository.save(kit);
    }

    // 3. SEU MÉTODO EXISTENTE (SALVAR FOTO)
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
}