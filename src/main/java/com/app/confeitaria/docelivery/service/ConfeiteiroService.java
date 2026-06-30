package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.ConfeiteiroDTO;
import com.app.confeitaria.docelivery.dto.LojaDTO;
import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import com.app.confeitaria.docelivery.model.entity.Loja;
import com.app.confeitaria.docelivery.model.entity.Produto;
import com.app.confeitaria.docelivery.model.repository.ConfeiteiroRepository;
import com.app.confeitaria.docelivery.model.repository.LojaRepository;
import com.app.confeitaria.docelivery.model.repository.ProdutoRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class ConfeiteiroService {

    @Autowired
    private ConfeiteiroRepository repository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String RAIZ_ARMAZENAMENTO = "C:/docelivery-storage/";

    @Transactional(readOnly = true)
    public ConfeiteiroDTO buscarConfeiteiroPorId(Long id) {
        Confeiteiro confeiteiro = buscarPorIdReal(id);
        return converterParaDTO(confeiteiro);
    }

    @Transactional(readOnly = true)
    public Confeiteiro buscarPorIdReal(Long id) {
        return repository.buscarComLojaPorId(id)
                .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado com o ID: " + id));
    }

    @Transactional
    public Confeiteiro atualizarConfeiteiro(Long id, Confeiteiro dadosAtualizados) {
        System.out.println("Recebido no Service para atualizar: " + dadosAtualizados.getNome() + " - CEP: " + dadosAtualizados.getCep());

        Confeiteiro confeiteiro = repository.buscarComLojaPorId(id)
                .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado com o ID: " + id));

        if (dadosAtualizados.getNome() != null) confeiteiro.setNome(dadosAtualizados.getNome());
        if (dadosAtualizados.getTelefone() != null) confeiteiro.setTelefone(dadosAtualizados.getTelefone());
        if (dadosAtualizados.getCpf() != null) confeiteiro.setCpf(dadosAtualizados.getCpf());
        if (dadosAtualizados.getCep() != null) confeiteiro.setCep(dadosAtualizados.getCep());
        if (dadosAtualizados.getEndereco() != null) confeiteiro.setEndereco(dadosAtualizados.getEndereco());
        if (dadosAtualizados.getBairro() != null) confeiteiro.setBairro(dadosAtualizados.getBairro());
        if (dadosAtualizados.getCidade() != null) confeiteiro.setCidade(dadosAtualizados.getCidade());
        if (dadosAtualizados.getUf() != null) confeiteiro.setUf(dadosAtualizados.getUf());
        if (dadosAtualizados.getDataNascimento() != null) confeiteiro.setDataNascimento(dadosAtualizados.getDataNascimento());
        if (dadosAtualizados.getEmail() != null) confeiteiro.setEmail(dadosAtualizados.getEmail());

        if (dadosAtualizados.getSenha() != null && !dadosAtualizados.getSenha().isBlank()) {
            confeiteiro.setSenha(passwordEncoder.encode(dadosAtualizados.getSenha()));
        }

        if (dadosAuthorized(dadosAtualizados.getProprietario())) confeiteiro.setProprietario(dadosAtualizados.getProprietario());
        if (dadosAtualizados.getCategoria() != null) confeiteiro.setCategoria(dadosAtualizados.getCategoria());
        if (dadosAtualizados.getPromocao() != null) confeiteiro.setPromocao(dadosAtualizados.getPromocao());

        if (dadosAtualizados.getLoja() != null && confeiteiro.getLoja() != null) {
            confeiteiro.getLoja().setNomeFantasia(dadosAtualizados.getLoja().getNomeFantasia());
            if (dadosAtualizados.getLoja().getDescricao() != null) confeiteiro.getLoja().setDescricao(dadosAtualizados.getLoja().getDescricao());
            if (dadosAtualizados.getLoja().getFotoUrl() != null) confeiteiro.getLoja().setFotoUrl(dadosAtualizados.getLoja().getFotoUrl());
        }

        return repository.save(confeiteiro);
    }

    private boolean dadosAuthorized(String proprietario) {
        return proprietario != null;
    }

    @Transactional
    public Confeiteiro atualizarPerfilLoja(Long idConfeiteiro, LojaDTO dto, MultipartFile imagem) {
        Confeiteiro confeiteiro = repository.buscarComLojaPorId(idConfeiteiro)
                .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado com o ID: " + idConfeiteiro));

        Loja loja = confeiteiro.getLoja();
        boolean lojaEhNova = (loja == null);
        if (lojaEhNova) {
            loja = new Loja();
        }

        if (dto.getNomeFantasia() != null && !dto.getNomeFantasia().isBlank()) {
            loja.setNomeFantasia(dto.getNomeFantasia());
        } else if (lojaEhNova) {
            loja.setNomeFantasia("Minha Loja");
        }

        String cnpjLimpo = dto.getCnpj() != null ? dto.getCnpj().replaceAll("[^0-9]", "") : "";
        if (!cnpjLimpo.isBlank()) {
            loja.setCnpj(cnpjLimpo);
        } else if (lojaEhNova) {
            loja.setCnpj("00000000000000");
        }

        if (dto.getTelefone() != null && !dto.getTelefone().isBlank()) {
            loja.setTelefone(dto.getTelefone());
        } else if (lojaEhNova) {
            loja.setTelefone("00000000000");
        }

        if (dto.getEndereco() != null && !dto.getEndereco().isBlank()) {
            loja.setEndereco(dto.getEndereco());
        } else if (lojaEhNova) {
            loja.setEndereco("A preencher");
        }

        loja.setDescricao(dto.getDescricao());

        if (imagem != null && !imagem.isEmpty()) {
            try {
                String extensao = imagem.getOriginalFilename().substring(imagem.getOriginalFilename().lastIndexOf("."));
                String nomeArquivoFinal = idConfeiteiro + "_perfil_loja" + extensao;

                Path diretorioDestino = Paths.get(RAIZ_ARMAZENAMENTO + "lojas/");
                if (!Files.exists(diretorioDestino)) {
                    Files.createDirectories(diretorioDestino);
                }

                Path caminhoCompletoArquivo = diretorioDestino.resolve(nomeArquivoFinal);
                System.out.println("[SERVICE] Escrevendo arquivo em: " + caminhoCompletoArquivo.toString());

                Files.copy(imagem.getInputStream(), caminhoCompletoArquivo, StandardCopyOption.REPLACE_EXISTING);

                String urlRelativaParaBanco = "/uploads/lojas/" + nomeArquivoFinal;
                loja.setFotoUrl(urlRelativaParaBanco);

            } catch (Exception e) {
                System.err.println("[ERRO CRÍTICO] Falha de I/O ao gravar imagem no disco: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Não foi possível salvar a imagem da loja. Tente novamente.");
            }
        } else if (dto.getFotoUrl() != null) {
            loja.setFotoUrl(dto.getFotoUrl());
        }

        loja.setStatus("ATIVO");
        confeiteiro.setLoja(loja);

        return repository.save(confeiteiro);
    }

    @Transactional(readOnly = true)
    public ConfeiteiroDTO obterPerfilPorEmail(String email) {
        Confeiteiro confeiteiroBase = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Perfil de confeiteiro não encontrado para o e-mail: " + email));

        Confeiteiro confeiteiroComLoja = repository.buscarComLojaPorId(confeiteiroBase.getId())
                .orElseThrow(() -> new RuntimeException("Erro ao carregar dados da loja do confeiteiro."));

        return converterParaDTO(confeiteiroComLoja);
    }

    @Transactional
    public void atualizarDadosGerais(Long idLoja, String novoNome) {
        Loja loja = lojaRepository.findById(idLoja)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada com o ID: " + idLoja));

        loja.setNomeFantasia(novoNome);
        lojaRepository.save(loja);
    }

    // ADICIONADO: Método completo que lista todas as lojas já mapeadas com o DTO correto
    @Transactional(readOnly = true)
    public List<ConfeiteiroDTO> listarTodasAsLojas() {
        List<Confeiteiro> confeiteiros = repository.findAll();
        return confeiteiros.stream()
                .map(this::converterParaDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    public ConfeiteiroDTO converterParaDTO(Confeiteiro confeiteiroEntity) {
        ConfeiteiroDTO dto = new ConfeiteiroDTO();
        dto.setId(confeiteiroEntity.getId());
        dto.setNome(confeiteiroEntity.getNome());
        dto.setEmail(confeiteiroEntity.getEmail());
        dto.setCpf(confeiteiroEntity.getCpf());
        dto.setTelefone(confeiteiroEntity.getTelefone());
        dto.setDataNascimento(confeiteiroEntity.getDataNascimento());
        dto.setTipoUsuario(confeiteiroEntity.getTipoUsuario());
        dto.setCodStatus(confeiteiroEntity.getCodStatus());
        dto.setCep(confeiteiroEntity.getCep());
        dto.setEndereco(confeiteiroEntity.getEndereco());
        dto.setBairro(confeiteiroEntity.getBairro());
        dto.setCidade(confeiteiroEntity.getCidade());
        dto.setUf(confeiteiroEntity.getUf());

        if (confeiteiroEntity.getLoja() != null) {
            LojaDTO lojaDTO = new LojaDTO();
            lojaDTO.setId(confeiteiroEntity.getLoja().getId());
            lojaDTO.setNomeFantasia(confeiteiroEntity.getLoja().getNomeFantasia());
            lojaDTO.setCnpj(confeiteiroEntity.getLoja().getCnpj());
            lojaDTO.setTelefone(confeiteiroEntity.getLoja().getTelefone());
            lojaDTO.setDescricao(confeiteiroEntity.getLoja().getDescricao());
            lojaDTO.setEndereco(confeiteiroEntity.getLoja().getEndereco());
            lojaDTO.setFotoUrl(confeiteiroEntity.getLoja().getFotoUrl());

            List<Produto> listaProdutos = produtoRepository.findKitsByConfeiteiroId(confeiteiroEntity.getId());
            lojaDTO.setProdutos(listaProdutos);

            dto.setLoja(lojaDTO);
        } else {
            dto.setLoja(null);
        }

        return dto;
    }
}