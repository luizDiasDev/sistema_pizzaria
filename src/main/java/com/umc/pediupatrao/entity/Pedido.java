package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    private String clienteId;
    private String clienteNome;
    private TipoEntrega tipoEntrega;
    private List<ItemPedido> itens = new ArrayList<>();

    private String status;

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal descontoPercentual = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String criadoPor;
    private Instant dataEntrada;

    private Instant dataSaida;
    private String saidaPor;

    private Instant dataFinalizacao;

    private String canceladoPor;
    private Instant dataCancelamento;
    private String justificativaCancelamento;

    private String tipoPizza;
    private String sabor;
    private Integer quantidade;

    public StatusPedido statusAtual() {
        return StatusPedido.parse(status).orElse(StatusPedido.RECEBIDO);
    }

    public TipoEntrega tipoEfetivo() {
        return tipoEntrega == null ? TipoEntrega.ENTREGA : tipoEntrega;
    }

    public StatusPedido proximoStatusEsperado() {
        return statusAtual().proximos(tipoEfetivo()).stream().findFirst().orElse(null);
    }

    public void recalcularTotais() {
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemPedido item : itens) {
            soma = soma.add(item.getSubtotal());
        }
        this.subtotal = soma.setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = descontoPercentual == null ? BigDecimal.ZERO : descontoPercentual;
        this.valorDesconto = subtotal.multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.total = subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);
    }

    public String resumoItens() {
        return itens.stream().map(ItemPedido::resumo).collect(Collectors.joining("; "));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(TipoEntrega tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens == null ? new ArrayList<>() : itens; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }

    public Instant getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(Instant dataEntrada) { this.dataEntrada = dataEntrada; }

    public Instant getDataSaida() { return dataSaida; }
    public void setDataSaida(Instant dataSaida) { this.dataSaida = dataSaida; }

    public String getSaidaPor() { return saidaPor; }
    public void setSaidaPor(String saidaPor) { this.saidaPor = saidaPor; }

    public Instant getDataFinalizacao() { return dataFinalizacao; }
    public void setDataFinalizacao(Instant dataFinalizacao) { this.dataFinalizacao = dataFinalizacao; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public Instant getDataCancelamento() { return dataCancelamento; }
    public void setDataCancelamento(Instant dataCancelamento) { this.dataCancelamento = dataCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public String getTipoPizza() { return tipoPizza; }
    public void setTipoPizza(String tipoPizza) { this.tipoPizza = tipoPizza; }

    public String getSabor() { return sabor; }
    public void setSabor(String sabor) { this.sabor = sabor; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
}
