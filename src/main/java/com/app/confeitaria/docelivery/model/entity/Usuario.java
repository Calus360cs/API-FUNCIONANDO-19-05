package com.app.confeitaria.docelivery.model.entity;

import com.app.confeitaria.docelivery.model.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cpf;
    private String cep;
    private String endereco;
    private String bairro;
    private String cidade;
    private String uf;
    private String telefone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    private LocalDate dataNascimento;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "loja_id", referencedColumnName = "id")
    @JsonManagedReference // 🟢 ADICIONE ESSA ANOTAÇÃO AQUI
    private Loja loja;

    // Removemos o insertable/updatable false para teste,
    // ou deixamos o JPA gerenciar via Discriminator
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", insertable = false, updatable = false)
    private TipoUsuario tipoUsuario;

    @Column(name = "cod_status")
    private Boolean codStatus = true;

    // --- MÉTODOS SPRING SECURITY (USERDETAILS) ---

    @Override public String getPassword() { return this.senha; }
    @Override public String getUsername() { return this.email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(this.codStatus); }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = (this.tipoUsuario != null) ? this.tipoUsuario.name() : "CLIENTE";
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));
    }
}