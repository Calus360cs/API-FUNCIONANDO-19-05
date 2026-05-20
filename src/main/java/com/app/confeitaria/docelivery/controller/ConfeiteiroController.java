package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import com.app.confeitaria.docelivery.model.entity.Loja;
import com.app.confeitaria.docelivery.model.entity.Usuario; // CORREÇÃO: Import da entidade Usuario adicionado
import com.app.confeitaria.docelivery.model.repository.ConfeiteiroRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// CORREÇÃO: Removido o @Lombok inexistente que quebrava a compilação
@RestController
@RequestMapping("/api/confeiteiro")
@CrossOrigin("*")
public class ConfeiteiroController {

    @Autowired
    private ConfeiteiroRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository; // CORREÇÃO: Injetado o repositório que faltava para o método /profile

    @GetMapping("/{id}")
    public ResponseEntity<?> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/atualizar/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> atualizarConfeiteiro(@PathVariable Long id, @ModelAttribute Confeiteiro dadosAtualizados) {
        try {
            Confeiteiro confeiteiro = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Confeiteiro não encontrado com o ID: " + id));

            if (dadosAtualizados.getNome() != null) {
                confeiteiro.setNome(dadosAtualizados.getNome());
            }
            if (dadosAtualizados.getTelefone() != null) {
                confeiteiro.setTelefone(dadosAtualizados.getTelefone());
            }

            if (dadosAtualizados.getLoja() != null && confeiteiro.getLoja() != null) {
                confeiteiro.getLoja().setNomeFantasia(dadosAtualizados.getLoja().getNomeFantasia());
            }

            Confeiteiro salvo = repository.save(confeiteiro);
            return ResponseEntity.ok(salvo);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao atualizar perfil: " + e.getMessage());
        }
    }

    @PutMapping("/perfil/loja")
    @org.springframework.transaction.annotation.Transactional // 🟢 CRÍTICO: Abre a transação para o Hibernate rastrear e salvar as alterações nas tabelas relacionadas
    public ResponseEntity<?> atualizarPerfilLoja(@RequestBody Confeiteiro dadosAtualizados) {
        try {
            return repository.findById(dadosAtualizados.getId())
                    .map(confeiteiroBanco -> {

                        // 1. Atualiza os dados básicos do Confeiteiro/Usuário
                        confeiteiroBanco.setNome(dadosAtualizados.getNome());
                        confeiteiroBanco.setEmail(dadosAtualizados.getEmail());

                        // 2. Se o formulário trouxe dados de loja e ela já existe no banco
                        if (dadosAtualizados.getLoja() != null && confeiteiroBanco.getLoja() != null) {
                            Loja lojaBanco = confeiteiroBanco.getLoja();
                            Loja lojaForm = dadosAtualizados.getLoja();

                            // Atualiza os campos da loja existente
                            lojaBanco.setNomeFantasia(lojaForm.getNomeFantasia());
                            lojaBanco.setDescricao(lojaForm.getDescricao());

                            // Se o formulário também trouxer esses campos, atualize-os para não perder dados:
                            if (lojaForm.getCnpj() != null) lojaBanco.setCnpj(lojaForm.getCnpj());
                            if (lojaForm.getTelefone() != null) lojaBanco.setTelefone(lojaForm.getTelefone());
                            if (lojaForm.getEndereco() != null) lojaBanco.setEndereco(lojaForm.getEndereco());

                            // 🟢 Sincroniza os dois lados em memória antes de salvar
                            lojaBanco.setConfeiteiro(confeiteiroBanco);
                            confeiteiroBanco.setLoja(lojaBanco);
                        }

                        // 3. Salva o Confeiteiro (o CascadeType.ALL na entidade Usuario vai empurrar as alterações para a tabela Loja)
                        Confeiteiro confeiteiroSalvo = repository.save(confeiteiroBanco);

                        return ResponseEntity.ok((Object) confeiteiroSalvo);
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao atualizar no banco: " + e.getMessage());
        }
    }

    @GetMapping("/profile")
    @org.springframework.transaction.annotation.Transactional // 🟢 Garante que a sessão do banco continue aberta para carregar a loja
    public ResponseEntity<?> obterPerfilPorEmail(@RequestParam String email) {
        return usuarioRepository.findByEmail(email)
                .map(usuario -> {
                    // Verifica se o usuário de fato é um Confeiteiro
                    if (usuario instanceof Confeiteiro) {
                        Confeiteiro confeiteiro = (Confeiteiro) usuario;

                        if (confeiteiro.getLoja() == null) {
                            Loja novaLoja = new Loja();
                            novaLoja.setNomeFantasia("Minha Nova Confeitaria");
                            novaLoja.setDescricao("Adicione uma descrição para a sua loja.");

                            // Vincula a Loja ao Confeiteiro (Lado inverso da relação)
                            novaLoja.setConfeiteiro(confeiteiro);

                            // Dados obrigatórios (nullable = false) para evitar erro de constraint no banco
                            novaLoja.setCnpj("00.000.000/0001-00");
                            novaLoja.setTelefone(confeiteiro.getTelefone() != null ? confeiteiro.getTelefone() : "000000000");
                            novaLoja.setEndereco("Endereço não informado");

                            // 🟢 O SEGREDO DO JPA AQUI:
                            // Vincula o Confeiteiro à Loja (Lado dono da relação que tem a FK no banco)
                            confeiteiro.setLoja(novaLoja);

                            // 🟢 GARANTA O SALVAMENTO COMPLETO:
                            // Salvando o confeiteiro, o JPA identifica a 'novaLoja' e faz o cascateamento no banco.
                            confeiteiro = repository.save(confeiteiro);
                        }

                        return ResponseEntity.ok((Object) confeiteiro);
                    }

                    // Se for um cliente comum, apenas retorna o usuário
                    return ResponseEntity.ok(usuario);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/public/lojas")
    public ResponseEntity<List<Confeiteiro>> listarLojas() {
        return ResponseEntity.ok(repository.findAll());
    }
}