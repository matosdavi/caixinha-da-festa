package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.caixinha.CaixinhaService.SituacaoPessoa;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.Lancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Painel → Lançamentos: depósito rápido, outros lançamentos e a lista. */
@Controller
@RequestMapping("/painel/lancamentos")
public class LancamentosController {

    private final LancamentoService lancamentos;
    private final PessoaService pessoas;
    private final CaixinhaService caixinha;
    private final Formato fmt;
    private final Clock clock;

    public LancamentosController(LancamentoService lancamentos, PessoaService pessoas, CaixinhaService caixinha,
                                 Formato fmt, Clock clock) {
        this.lancamentos = lancamentos;
        this.pessoas = pessoas;
        this.caixinha = caixinha;
        this.fmt = fmt;
        this.clock = clock;
    }

    /** Uma opção do campo "pessoa", com o valor que falta para ficar em dia (sugerido no depósito). */
    public record OpcaoPessoa(Long id, String nome, String sugestao, String detalhe) {
    }

    @GetMapping
    public String listar(@RequestParam(required = false) Long pessoa,
                         @RequestParam(required = false) String valor,
                         @RequestParam(required = false) TipoLancamento tipo,
                         Model model) {
        FormularioLancamento form = new FormularioLancamento();
        form.setData(LocalDate.now(clock).toString());
        form.setPessoaId(pessoa);
        form.setValor(valor == null ? "" : valor);
        if (tipo != null) {
            form.setTipo(tipo);
        }
        return pagina(model, form);
    }

    @PostMapping
    public String registrar(@ModelAttribute("form") FormularioLancamento form, Model model,
                            RedirectAttributes redirect) {
        try {
            Lancamento l = lancamentos.registrar(form.paraDados());
            String deQuem = l.getPessoa() == null ? "" : " de " + l.getPessoa().getNome();
            redirect.addFlashAttribute("sucesso", l.getTipo().getRotulo() + deQuem + " (" + fmt.reais(l.getValor())
                    + ") lançado.");
            return "redirect:/painel/lancamentos";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, form);
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            lancamentos.excluir(id);
            redirect.addFlashAttribute("sucesso", "Lançamento excluído.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/lancamentos";
    }

    private String pagina(Model model, FormularioLancamento form) {
        Map<Long, SituacaoPessoa> situacoes = caixinha.resumo().situacoes().stream()
                .collect(Collectors.toMap(s -> s.pessoa().getId(), Function.identity()));

        List<OpcaoPessoa> opcoes = pessoas.listar().stream()
                .map(p -> opcao(p, situacoes.get(p.getId())))
                .toList();

        model.addAttribute("form", form);
        model.addAttribute("pessoas", opcoes);
        model.addAttribute("tipos", TipoLancamento.values());
        model.addAttribute("categorias", Categoria.values());
        model.addAttribute("lancamentos", lancamentos.listar());
        return "painel/lancamentos";
    }

    private OpcaoPessoa opcao(Pessoa p, SituacaoPessoa s) {
        String detalhe;
        String sugestao = "";
        if (!p.isAtivo()) {
            detalhe = "inativa";
        } else if (s == null) {
            detalhe = "não paga";
        } else {
            BigDecimal falta = s.situacao().valorParaFicarEmDia();
            sugestao = falta.signum() > 0 ? fmt.campo(falta) : "";
            detalhe = falta.signum() > 0 ? "falta " + fmt.reais(falta) : "em dia";
        }
        return new OpcaoPessoa(p.getId(), p.getNome(), sugestao, detalhe);
    }
}
