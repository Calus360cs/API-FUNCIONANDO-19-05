package com.app.confeitaria.docelivery.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    // Antes: @OneToOne(fetch = FetchType.LAZY)
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "confeiteiro_id")
    @JsonIgnoreProperties("loja")
    private Confeiteiro confeiteiro;

    @OneToMany(mappedBy = "loja", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Produto> produtos;

    @Column(name = "nome_fantasia", nullable = false, length = 100)
    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(nullable = false)
    private String telefone;

    @Column(nullable = false)
    private String endereco;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private String status; // Deixamos sem valor fixo aqui para não sobrescrever o banco

    @Column(name = "data_cadastro")
    private LocalDateTime dataCadastro;

    @Column(name = "foto_url")
    private String fotoUrl;

    @PrePersist
    protected void onCreate() {
        this.dataCadastro = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDENTE"; // Só vira PENDENTE se for uma loja nova criada do zero
        }
    }

    // MÉTODO AUXILIAR: Garante que os dois lados fiquem vinculados no banco
    public void vincularConfeiteiro(Confeiteiro confeiteiro) {
        this.confeiteiro = confeiteiro;
        if (confeiteiro != null && confeiteiro.getLoja() != this) {
            confeiteiro.setLoja(this);
        }
    }
}