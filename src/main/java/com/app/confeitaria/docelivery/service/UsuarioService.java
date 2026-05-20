package com.app.confeitaria.docelivery.service;

import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import com.app.confeitaria.docelivery.model.entity.Usuario;
import com.app.confeitaria.docelivery.model.repository.ConfeiteiroRepository;
import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder; // Adicione isso!
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfeiteiroRepository confeiteiroRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Essencial para comparar senhas criptografadas

    // CORREÇÃO DEFINITIVA:
    public Optional<Usuario> realizarLogin(String email, String senha) {
        // Como o repository já retorna Optional<Usuario>, chamamos direto:
        return usuarioRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(senha, user.getSenha()));
    }

    @Transactional
    public Confeiteiro salvarConfeiteiro(Confeiteiro confeiteiro) {
        if (usuarioRepository.existsByCpf(confeiteiro.getCpf())) {
            throw new RuntimeException("CPF já cadastrado no sistema!");
        }
        // Criptografa a senha antes de salvar no banco
        confeiteiro.setSenha(passwordEncoder.encode(confeiteiro.getSenha()));
        return confeiteiroRepository.save(confeiteiro);
    }

    public List<Confeiteiro> listarLojasPorCidade(String cidade) {
        return confeiteiroRepository.findByCidadeIgnoreCase(cidade);
    }
}