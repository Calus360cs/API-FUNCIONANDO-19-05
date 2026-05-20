package com.app.confeitaria.docelivery.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "loja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "loja")
    @JsonBackReference // 🟢 SUBSTITUA O @JsonIgnore POR ESTA ANOTAÇÃO
    private Confeiteiro confeiteiro;

    @OneToMany(mappedBy = "loja", fetch = FetchType.LAZY)
    @JsonIgnore // 🟢 Garanta que este continue aqui para não vazar a lista infinita de produtos no perfil
    private List<Produto> produtos;

    @Column(nullable = false, length = 100)
    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(nullable = false)
    private String telefone;

    @Column(nullable = false)
    private String endereco;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private String status = "PENDENTE";

    private LocalDateTime dataCadastro;

    // 💡 APENAS ADICIONE ESSA LINHA:
    private String fotoUrl;

    @PrePersist
    protected void onCreate() {
        dataCadastro = LocalDateTime.now();
    }
}