package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.DivisaoFamilia;
import com.natal.caixinha.caixinha.FamiliaService;
import com.natal.caixinha.caixinha.FamiliaService.Familia;
import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Painel → Lançamentos → Pagamento da família: um PIX da família vira um depósito por pessoa. */
@Controller
@RequestMapping("/painel/lancamentos/familia")
public class PagamentoFamiliaController {

    private final FamiliaService familias;
    private final LancamentoService lancamentos;
    private final PessoaRepository pessoas;
    private final Formato fmt;
    private final Clock clock;

    public PagamentoFamiliaController(FamiliaService familias, LancamentoService lancamentos,
                                      PessoaRepository pessoas, Formato fmt, Clock clock) {
        this.familias = familias;
        this.lancamentos = lancamentos;
        this.pessoas = pessoas;
        this.fmt = fmt;
        this.clock = clock;
    }

    /** Uma família no seletor. */
    public record OpcaoFamilia(Long id, String nome) {
    }

    /** Uma linha de parte: a pessoa e o valor já como texto de campo. */
    public record LinhaParte(Long pessoaId, String nome, String peso, String valor) {
    }

    @GetMapping
    public String formulario(@RequestParam(required = false) Long responsavel,
                             @RequestParam(required = false) Long responsavelId,
                             @RequestParam(required = false) String total,
                             @RequestParam(required = false) String data,
                             Model model) {
        Long escolhido = responsavel != null ? responsavel : responsavelId;
        String dataTexto = data == null || data.isBlank() ? LocalDate.now(clock).toString() : data;
        if (escolhido == null) {
            return pagina(model, null, null, "", dataTexto, Map.of());
        }
        try {
            Familia f = familias.daFamilia(escolhido);
            BigDecimal valor = total == null || total.isBlank() ? f.valorPix() : Dinheiro.ler(total, "Valor do PIX");
            Map<Long, String> sugestao = new HashMap<>();
            if (valor != null && valor.signum() > 0) {
                for (DivisaoFamilia.Parte p : familias.sugerirDivisao(escolhido, valor)) {
                    sugestao.put(p.pessoaId(), fmt.campo(p.valor()));
                }
            }
            return pagina(model, escolhido, f, valor == null ? "" : fmt.campo(valor), dataTexto, sugestao);
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, escolhido, familias.daFamilia(escolhido), total, dataTexto, Map.of());
        }
    }

    @PostMapping
    public String lancar(@RequestParam(required = false) Long responsavelId,
                         @RequestParam(required = false) String total,
                         @RequestParam(required = false) String data,
                         @RequestParam Map<String, String> parametros,
                         Model model, RedirectAttributes redirect) {
        Map<Long, String> digitadas = new HashMap<>();
        parametros.forEach((nome, valor) -> {
            if (nome.startsWith("parte_")) {
                try {
                    digitadas.put(Long.valueOf(nome.substring("parte_".length())), valor);
                } catch (NumberFormatException ignorado) {
                    // parâmetro estranho: não é de nenhuma pessoa
                }
            }
        });
        try {
            List<DivisaoFamilia.Parte> partes = new ArrayList<>();
            for (var parte : digitadas.entrySet()) {
                partes.add(new DivisaoFamilia.Parte(parte.getKey(), Dinheiro.ler(parte.getValue(), "Cada parte")));
            }
            var criados = lancamentos.registrarPagamentoDaFamilia(responsavelId, Dinheiro.ler(total, "Valor do PIX"),
                    lerData(data), partes);
            redirect.addFlashAttribute("sucesso", "Pagamento da família lançado: " + criados.size()
                    + (criados.size() == 1 ? " depósito." : " depósitos."));
            return "redirect:/painel/lancamentos";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            Familia f = responsavelId == null ? null : familias.daFamilia(responsavelId);
            return pagina(model, responsavelId, f, total, data, digitadas);
        }
    }

    private static LocalDate lerData(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(data);
        } catch (DateTimeParseException e) {
            throw new RegraException("Data inválida.");
        }
    }

    private String pagina(Model model, Long escolhido, Familia familia, String total, String data,
                          Map<Long, String> valores) {
        Map<Long, Long> responsavelDe = familias.responsavelDasFamiliasComVariosPagantes();
        List<OpcaoFamilia> opcoes = pessoas.findAllById(new java.util.HashSet<>(responsavelDe.values())).stream()
                .sorted(java.util.Comparator.comparing(Pessoa::getNome))
                .map(p -> new OpcaoFamilia(p.getId(), p.getNome()))
                .toList();
        List<LinhaParte> linhas = familia == null ? List.of() : familia.pagantes().stream()
                .map(m -> new LinhaParte(m.pessoa().getId(), m.pessoa().getNome(), fmt.peso(m.pessoa().getPeso()),
                        valores.getOrDefault(m.pessoa().getId(), "")))
                .toList();
        model.addAttribute("familias", opcoes);
        model.addAttribute("escolhido", escolhido);
        model.addAttribute("familia", familia);
        model.addAttribute("total", total == null ? "" : total);
        model.addAttribute("data", data == null ? "" : data);
        model.addAttribute("partes", linhas);
        return "painel/pagamento-familia";
    }
}
