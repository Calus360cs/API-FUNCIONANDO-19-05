package com.app.confeitaria.docelivery.model.repository;

import com.app.confeitaria.docelivery.model.entity.Produto;
import com.app.confeitaria.docelivery.model.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // 🟢 CORREÇÃO: Alterado de p.itensDoKit para p.itens para refletir a nova modelagem de quantidade
    @Query("SELECT DISTINCT p FROM Produto p LEFT JOIN FETCH p.itens WHERE p.confeiteiro.id = :id")
    List<Produto> findByConfeiteiroId(@Param("id") Long id);

    @Query("SELECT DISTINCT p.categoria FROM Produto p WHERE p.categoria IS NOT NULL")
    List<Categoria> findDistinctCategorias();
}