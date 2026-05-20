package com.app.confeitaria.docelivery.model.repository;

import com.app.confeitaria.docelivery.model.entity.Confeiteiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfeiteiroRepository extends JpaRepository<Confeiteiro, Long> {

    // Busca pelo e-mail (campo herdado de Usuario)
    Optional<Confeiteiro> findByEmail(String email);

    // Busca pelo CNPJ (campo específico de Confeiteiro)
    Optional<Confeiteiro> findByLoja_Cnpj(String cnpj);

    /**
     * CORREÇÃO: Busca por cidade (campo herdado de Usuario)
     * Usamos JPQL para garantir que o Spring localize o atributo na hierarquia de classes.
     */
    @Query("SELECT c FROM Confeiteiro c WHERE UPPER(c.cidade) = UPPER(:cidade)")
    List<Confeiteiro> findByCidadeIgnoreCase(@Param("cidade") String cidade);

    // CORREÇÃO: Ajustado com o underline para navegar corretamente até a entidade Loja e ler o atributo nome
    Optional<Confeiteiro> findByLoja_NomeFantasiaIgnoreCase(String nomeLoja);
}