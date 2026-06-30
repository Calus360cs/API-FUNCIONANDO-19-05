package com.app.confeitaria.docelivery.dto;

import java.math.BigDecimal;

public class PagamentoDTO {
    private BigDecimal transactionAmount; // Valor do pedido
    private String paymentMethodId;       // "pix", "visa", "master", "bolbradesco"
    private String email;                 // E-mail do comprador de teste
    private String token;                 // OBRIGATÓRIO APENAS PARA CARTÃO (gerado pelo front)

    // Getters e Setters
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }

    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}