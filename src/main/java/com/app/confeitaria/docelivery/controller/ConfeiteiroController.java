package com.app.confeitaria.docelivery.controller;

import com.app.confeitaria.docelivery.dto.ConfeiteiroDTO;
import com.app.confeitaria.docelivery.dto.LojaDTO;
import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import com.app.confeitaria.docelivery.model.repository.ConfeiteiroRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import com.app.confeitaria.docelivery.service.ConfeiteiroService;
import com.fasterxml.jackson.databind.ObjectMapper; // Mantém a importação
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/confeiteiro")
public class ConfeiteiroController {

    @Autowired
    private ConfeiteiroRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfeiteiroService confeiteiroService;

    // CORREÇÃO SÊNIOR: Removido o @Autowired que causava o travamento na inicialização.
    // Instanciar diretamente o ObjectMapper elimina a necessidade de o Spring procurar um Bean gerenciado.
    // Como a classe é thread-safe para operações de leitura/escrita, essa prática é segura e performática.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{id}")
    public ResponseEntity<?> buscar(@PathVariable Long id) {
        try {
            ConfeiteiroDTO confeiteiroDTO = confeiteiroService.buscarConfeiteiroPorId(id);
            return ResponseEntity.ok(confeiteiroDTO);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarConfeiteiro(@PathVariable Long id, @RequestBody Confeiteiro dadosAtualizados) {
        try {
            Confeiteiro salvo = confeiteiroService.atualizarConfeiteiro(id, dadosAtualizados);
            return ResponseEntity.ok(salvo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao atualizar perfil: " + e.getMessage());
        }
    }

    @PutMapping(value = "/loja/atualizar/{idConfeiteiro}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> atualizarPerfilLoja(
            @PathVariable Long idConfeiteiro,
            @RequestPart("dados") String lojaDtoJson,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem
    ) {
        try {
            System.out.println("[API] Processando atualização de loja multipart para o Confeiteiro ID: " + idConfeiteiro);

            // O uso aqui continua idêntico, mas agora sem depender da injeção do Spring
            LojaDTO lojaDTO = objectMapper.readValue(lojaDtoJson, LojaDTO.class);

            Confeiteiro confeiteiroAtualizado = confeiteiroService.atualizarPerfilLoja(idConfeiteiro, lojaDTO, imagem);

            return ResponseEntity.ok(confeiteiroAtualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Erro interno ao processar dados da loja: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> obterPerfilPorEmail(@RequestParam String email) {
        try {
            Object perfil = confeiteiroService.obterPerfilPorEmail(email);
            return ResponseEntity.ok(perfil);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/public/lojas")
    public ResponseEntity<List<Confeiteiro>> listarLojas() {
        return ResponseEntity.ok(repository.findAll());
    }
}