package com.natal.caixinha.orcamento;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Itens do orçamento. A soma dos valores previstos é a base da meta e da cota. */
@Service
public class OrcamentoService {

    static final int TAMANHO_DESCRICAO = 200;

    private final ItemOrcamentoRepository itens;

    public OrcamentoService(ItemOrcamentoRepository itens) {
        this.itens = itens;
    }

    public record DadosItem(Categoria categoria, String descricao, BigDecimal valorPrevisto, BigDecimal valorReal) {
    }

    @Transactional(readOnly = true)
    public List<ItemOrcamento> listar() {
        return itens.findAllByOrderByCategoriaAscIdAsc();
    }

    @Transactional(readOnly = true)
    public ItemOrcamento buscar(Long id) {
        return itens.findById(id).orElseThrow(() -> new RegraException("Item do orçamento não encontrado."));
    }

    @Transactional(readOnly = true)
    public BigDecimal somaPrevista() {
        return itens.somaPrevista().setScale(2);
    }

    /** Cria (id nulo) ou altera um item. */
    @Transactional
    public ItemOrcamento salvar(Long id, DadosItem d) {
        if (d.categoria() == null) {
            throw new RegraException("Escolha a categoria.");
        }
        String descricao = d.descricao() == null ? "" : d.descricao().strip().replaceAll("\\s+", " ");
        if (descricao.isEmpty()) {
            throw new RegraException("Descreva o item (ex.: \"Aluguel da chácara\").");
        }
        if (descricao.length() > TAMANHO_DESCRICAO) {
            throw new RegraException("A descrição pode ter no máximo " + TAMANHO_DESCRICAO + " caracteres.");
        }
        if (d.valorPrevisto() == null) {
            throw new RegraException("Informe o valor previsto.");
        }
        BigDecimal previsto = Dinheiro.naoNegativo(d.valorPrevisto(), "Valor previsto");
        BigDecimal real = Dinheiro.naoNegativo(d.valorReal(), "Valor real");

        if (id == null) {
            return itens.save(new ItemOrcamento(d.categoria(), descricao, previsto, real));
        }
        ItemOrcamento item = buscar(id);
        item.alterar(d.categoria(), descricao, previsto, real);
        return item;
    }

    @Transactional
    public void excluir(Long id) {
        itens.delete(buscar(id));
    }
}
