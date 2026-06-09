package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.dto.ConfeiteiroDTO;
import com.app.confeitaria.docelivery.dto.LojaDTO;
import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import com.app.confeitaria.docelivery.model.entity.Loja;
import com.app.confeitaria.docelivery.model.entity.Usuario;
import com.app.confeitaria.docelivery.model.repository.ConfeiteiroRepository;
import com.app.confeitaria.docelivery.model.repository.LojaRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfeiteiroService {

    @Autowired
    private ConfeiteiroRepository repository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * MÉTODO ADICIONADO PARA O CONTROLLER FUNCIONAR DIREITO:
     * Ele busca a entidade real com o cache limpo e converte para o DTO esperado pelo front-end.
     */
    @Transactional(readOnly = true)
    public ConfeiteiroDTO buscarConfeiteiroPorId(Long id) {
        // Aproveita a busca do buscarPorIdReal que limpa o cache do Hibernate
        Confeiteiro confeiteiro = buscarPorIdReal(id);

        // Converte a entidade Confeiteiro encontrada para o ConfeiteiroDTO
        return converterParaDTO(confeiteiro);
    }

    /**
     * 🟢 NOVO MÉTODO CRÍTICO: Usado pelo seu Controller para buscar os dados reais por ID
     * Força a limpeza do cache de sessão para garantir que o nó da loja não venha nulo
     */
    @Transactional(readOnly = true)
    public Confeiteiro buscarPorIdReal(Long id) {
        // Limpa o cache local do Hibernate para forçar o SQL com LEFT JOIN FETCH
        entityManager.clear();

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

        if (dadosAtualizados.getLoja() != null && confeiteiro.getLoja() != null) {
            confeiteiro.getLoja().setNomeFantasia(dadosAtualizados.getLoja().getNomeFantasia());
            if (dadosAtualizados.getLoja().getDescricao() != null) confeiteiro.getLoja().setDescricao(dadosAtualizados.getLoja().getDescricao());
            if (dadosAtualizados.getLoja().getFotoUrl() != null) confeiteiro.getLoja().setFotoUrl(dadosAtualizados.getLoja().getFotoUrl());
        }

        return repository.save(confeiteiro);
    }

    @Transactional
    public Confeiteiro atualizarPerfilLoja(Long idConfeiteiro, LojaDTO dto) {
        // 1. Busca o confeiteiro real
        Confeiteiro confeiteiro = repository.buscarComLojaPorId(idConfeiteiro)
                .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado com o ID: " + idConfeiteiro));

        // 2. Tenta buscar a loja pelo ID do confeiteiro ou reaproveita a que está na entidade
        Loja loja = lojaRepository.findByConfeiteiroId(idConfeiteiro)
                .orElseGet(() -> confeiteiro.getLoja() != null ? confeiteiro.getLoja() : new Loja());

        // 3. Atualiza os campos vindos do DTO
        if (dto.getNomeFantasia() != null && !dto.getNomeFantasia().trim().isEmpty()) {
            loja.setNomeFantasia(dto.getNomeFantasia());
        }

        // Verificação de CNPJ: Garanta que o CNPJ enviado no Front não seja repetido no banco por outro ID
        if (dto.getCnpj() != null && !dto.getCnpj().trim().isEmpty()) {
            loja.setCnpj(dto.getCnpj().replaceAll("[^0-9]", "")); // Limpa pontos e barras se houver
        }

        if (dto.getTelefone() != null && !dto.getTelefone().trim().isEmpty()) {
            loja.setTelefone(dto.getTelefone());
        }
        if (dto.getEndereco() != null && !dto.getEndereco().trim().isEmpty()) {
            loja.setEndereco(dto.getEndereco());
        }

        loja.setDescricao(dto.getDescricao());

        if (dto.getFotoUrl() != null) {
            loja.setFotoUrl(dto.getFotoUrl());
        }

        loja.setStatus("ATIVO");

        // 4. Estabelece o vínculo bidirecional estrito antes de salvar
        loja.vincularConfeiteiro(confeiteiro);
        confeiteiro.setLoja(loja);

        // 5. Salva primeiro a loja para garantir a persistência da FK, depois atualiza o confeiteiro
        Loja lojaSalva = lojaRepository.save(loja);
        confeiteiro.setLoja(lojaSalva);

        return repository.save(confeiteiro);
    }

    @Transactional(readOnly = true)
    public Object obterPerfilPorEmail(String email) {
        entityManager.clear(); // Garante dados novos na busca por email
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (usuario instanceof Confeiteiro) {
            Confeiteiro confeiteiro = repository.buscarComLojaPorId(usuario.getId())
                    .orElse((Confeiteiro) usuario);

            if (confeiteiro.getLoja() == null) {
                Loja lojaProvisoria = new Loja();
                lojaProvisoria.setNomeFantasia("Preencha o nome da sua Confeitaria");
                lojaProvisoria.setDescricao("Clique em editar para adicionar uma descrição.");
                lojaProvisoria.setStatus("PENDENTE");
                confeiteiro.setLoja(lojaProvisoria);
            }
            return confeiteiro;
        }
        return usuario;
    }

    @Transactional
    public void atualizarDadosGerais(Long idLoja, String novoNome) {
        Loja loja = lojaRepository.findById(idLoja)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada com o ID: " + idLoja));

        loja.setNomeFantasia(novoNome);
        lojaRepository.save(loja);
    }


    public ConfeiteiroDTO converterParaDTO(Usuario confeiteiroEntity) {
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

        // Instancia e converte a loja apenas se o confeiteiro possuir uma cadastrada
        if (confeiteiroEntity.getLoja() != null) {
            LojaDTO lojaDTO = new LojaDTO();
            lojaDTO.setId(confeiteiroEntity.getLoja().getId());
            lojaDTO.setNomeFantasia(confeiteiroEntity.getLoja().getNomeFantasia());
            lojaDTO.setCnpj(confeiteiroEntity.getLoja().getCnpj());
            lojaDTO.setTelefone(confeiteiroEntity.getLoja().getTelefone());
            lojaDTO.setDescricao(confeiteiroEntity.getLoja().getDescricao());
            lojaDTO.setEndereco(confeiteiroEntity.getLoja().getEndereco());
            lojaDTO.setFotoUrl(confeiteiroEntity.getLoja().getFotoUrl());

            dto.setLoja(lojaDTO); // Vincula a subclasse loja ao DTO principal
        } else {
            dto.setLoja(null); // Explicitamente nulo se não houver loja vinculada
        }

        return dto;
    }
}