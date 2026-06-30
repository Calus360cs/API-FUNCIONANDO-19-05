package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.model.entity.Cliente;
import com.app.confeitaria.docelivery.model.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cliente")
public class ClienteController {

    private static final Logger log = LoggerFactory.getLogger(ClienteController.class);

    @Autowired
    private ClienteRepository clienteRepository;

    @PutMapping("/atualizar/{id}")
    @Transactional
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody Cliente dados) {
        log.info("Requisição recebida para atualizar Cliente ID: {}", id);

        return clienteRepository.findById(id).map(clienteExistente -> {

            // 1. Atualiza os campos básicos da classe pai (Usuario)
            if (dados.getNome() != null) clienteExistente.setNome(dados.getNome());
            if (dados.getTelefone() != null) clienteExistente.setTelefone(dados.getTelefone());
            if (dados.getEmail() != null) clienteExistente.setEmail(dados.getEmail());
            if (dados.getCpf() != null) clienteExistente.setCpf(dados.getCpf());
            if (dados.getCep() != null) clienteExistente.setCep(dados.getCep());
            if (dados.getEndereco() != null) clienteExistente.setEndereco(dados.getEndereco());
            if (dados.getBairro() != null) clienteExistente.setBairro(dados.getBairro());
            if (dados.getCidade() != null) clienteExistente.setCidade(dados.getCidade());
            if (dados.getUf() != null) clienteExistente.setUf(dados.getUf());

            // 🔴 CORREÇÃO: Mapeando os campos que o formulário envia, mas o banco ignorava
            if (dados.getDataNascimento() != null) clienteExistente.setDataNascimento(dados.getDataNascimento());
            if (dados.getCodStatus() != null) clienteExistente.setCodStatus(dados.getCodStatus());

            // ⚠️ NOTA SOBRE A SENHA: Se o seu formulário do Front-end enviar uma nova senha,
            // descomente as linhas abaixo. Se você usar Spring Security com criptografia,
            // lembre-se de injetar o passwordEncoder para não salvar a senha em texto puro.
            /*
            if (dados.getSenha() != null && !dados.getSenha().isBlank()) {
                clienteExistente.setSenha(dados.getSenha());
            }
            */

            // 2. Atualiza campos específicos da classe filha (Cliente)
            if (dados.getApelido() != null) clienteExistente.setApelido(dados.getApelido());

            // 3. Salva efetivamente a entidade atualizada
            Cliente salvo = clienteRepository.save(clienteExistente);
            log.info("Cliente ID: {} [ {} ] atualizado com sucesso no banco de dados.", salvo.getId(), salvo.getNome());

            return ResponseEntity.ok(salvo);

        }).orElseGet(() -> {
            log.warn("Falha na atualização: Usuário com ID {} não foi encontrado ou não é um CLIENTE.", id);
            return ResponseEntity.notFound().build();
        });
    }
}