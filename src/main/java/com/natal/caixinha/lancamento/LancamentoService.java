package com.natal.caixinha.lancamento;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lançamentos da conta. Só o organizador lança (o painel exige login).
 * Um lançamento errado é excluído e lançado de novo: o saldo é sempre recalculado.
 */
@Service
public class LancamentoService {

    static final int TAMANHO_DESCRICAO = 200;
    static final int TAMANHO_URL = 500;

    private final LancamentoRepository lancamentos;
    private final PessoaRepository pessoas;
    private final Clock clock;

    public LancamentoService(LancamentoRepository lancamentos, PessoaRepository pessoas, Clock clock) {
        this.lancamentos = lancamentos;
        this.pessoas = pessoas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Lancamento> listar() {
        return lancamentos.listarRecentes();
    }

    @Transactional(readOnly = true)
    public List<Lancamento> daPessoa(Long pessoaId) {
        return lancamentos.daPessoa(pessoaId);
    }

    @Transactional
    public Lancamento registrar(DadosLancamento d) {
        if (d.tipo() == null) {
            throw new RegraException("Escolha o tipo do lançamento.");
        }
        TipoLancamento tipo = d.tipo();
        BigDecimal valor = Dinheiro.positivo(d.valor(), "Valor");

        LocalDate hoje = LocalDate.now(clock);
        if (d.data() == null) {
            throw new RegraException("Informe a data.");
        }
        if (d.data().isAfter(hoje)) {
            throw new RegraException("A data não pode ser no futuro: use a data em que o dinheiro entrou ou saiu da conta.");
        }

        Pessoa pessoa = null;
        if (tipo.isDePessoa()) {
            if (d.pessoaId() == null) {
                throw new RegraException("Escolha a pessoa do lançamento.");
            }
            pessoa = pessoas.findById(d.pessoaId()).orElseThrow(() -> new RegraException("Pessoa não encontrada."));
        }

        Categoria categoria = null;
        if (tipo == TipoLancamento.GASTO) {
            if (d.categoria() == null) {
                throw new RegraException("Escolha a categoria do gasto.");
            }
            categoria = d.categoria();
        }

        if (tipo == TipoLancamento.DEVOLUCAO) {
            BigDecimal guardado = guardadoDe(pessoa.getId());
            if (valor.compareTo(guardado) > 0) {
                throw new RegraException("A devolução (R$ " + valor + ") é maior do que " + pessoa.getNome()
                        + " tem guardado (R$ " + guardado + ").");
            }
        }

        String descricao = textoOpcional(d.descricao(), TAMANHO_DESCRICAO, "A descrição");
        String url = textoOpcional(d.comprovanteUrl(), TAMANHO_URL, "O link do comprovante");
        if (url != null && !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new RegraException("O link do comprovante deve começar com https://");
        }

        return lancamentos.save(new Lancamento(tipo, pessoa, valor, d.data(), descricao, categoria, url,
                OffsetDateTime.now(clock)));
    }

    @Transactional
    public void excluir(Long id) {
        if (!lancamentos.existsById(id)) {
            throw new RegraException("Lançamento não encontrado.");
        }
        lancamentos.deleteById(id);
    }

    /** guardado = depósitos + crédito do ano anterior − devoluções. */
    @Transactional(readOnly = true)
    public BigDecimal guardadoDe(Long pessoaId) {
        return lancamentos.somaDaPessoa(pessoaId, TipoLancamento.DEPOSITO)
                .add(lancamentos.somaDaPessoa(pessoaId, TipoLancamento.CREDITO_ANO_ANTERIOR))
                .subtract(lancamentos.somaDaPessoa(pessoaId, TipoLancamento.DEVOLUCAO))
                .setScale(2);
    }

    private static String textoOpcional(String texto, int tamanho, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.strip().replaceAll("\\s+", " ");
        if (limpo.length() > tamanho) {
            throw new RegraException(campo + " pode ter no máximo " + tamanho + " caracteres.");
        }
        return limpo;
    }
}
