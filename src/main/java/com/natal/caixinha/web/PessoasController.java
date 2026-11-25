package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaService;
import com.natal.caixinha.pessoa.Telefones;
import com.natal.caixinha.whatsapp.LinkWhatsApp;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Painel → Pessoas: cadastro, responsáveis e links pessoais. */
@Controller
@RequestMapping("/painel/pessoas")
public class PessoasController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PessoaService pessoas;
    private final LinkService links;
    private final CaixinhaRepository caixinhas;

    public PessoasController(PessoaService pessoas, LinkService links, CaixinhaRepository caixinhas) {
        this.pessoas = pessoas;
        this.links = links;
        this.caixinhas = caixinhas;
    }

    /** Uma linha da lista, já pronta para a tela. */
    public record LinhaPessoa(Long id, String nome, String telefone, String peso, String responsavel,
                              List<String> dependentes, boolean ativo, boolean responsavelProprio,
                              String link, String whatsapp, String entrada, String observacao) {
    }

    @GetMapping
    public String listar(Model model) {
        List<Pessoa> todas = pessoas.listar();
        Map<Long, String> tokens = links.tokensAtivos();
        String base = Enderecos.base();
        String nomeCaixinha = caixinhas.configuracao().getNome();

        List<LinhaPessoa> linhas = new ArrayList<>();
        int semLink = 0;
        BigDecimal somaPesos = BigDecimal.ZERO;
        int pagantes = 0;
        for (Pessoa p : todas) {
            List<String> dependentes = todas.stream()
                    .filter(d -> d.isAtivo() && d.getResponsavel() != null && d.getResponsavel().getId().equals(p.getId()))
                    .map(Pessoa::getNome).toList();
            String token = tokens.get(p.getId());
            String link = token == null ? null : base + "/c/" + token;
            boolean responsavelProprio = p.isAtivo() && p.isResponsavel();
            if (responsavelProprio && link == null) {
                semLink++;
            }
            if (p.isAtivo()) {
                somaPesos = somaPesos.add(p.getPeso());
                if (p.isPagante()) {
                    pagantes++;
                }
            }
            linhas.add(new LinhaPessoa(p.getId(), p.getNome(), Telefones.formatar(p.getTelefone()),
                    descreverPeso(p.getPeso()), p.getResponsavel() == null ? null : p.getResponsavel().getNome(),
                    dependentes, p.isAtivo(), responsavelProprio, link,
                    link == null ? null : LinkWhatsApp.para(p.getTelefone(), mensagemDoLink(p, dependentes, link, nomeCaixinha)),
                    p.getDataEntrada().format(DATA), p.getObservacao()));
        }

        model.addAttribute("linhas", linhas);
        model.addAttribute("semLink", semLink);
        model.addAttribute("ativas", todas.stream().filter(Pessoa::isAtivo).count());
        model.addAttribute("pagantes", pagantes);
        model.addAttribute("somaPesos", somaPesos.stripTrailingZeros().toPlainString().replace('.', ','));
        return "painel/pessoas";
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        FormularioPessoa form = new FormularioPessoa();
        form.setDataEntrada(LocalDate.now().toString());
        return formulario(model, form, null);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return formulario(model, FormularioPessoa.de(DadosPessoa.de(pessoas.buscar(id))), id);
    }

    @PostMapping
    public String criar(@ModelAttribute("form") FormularioPessoa form, Model model, RedirectAttributes redirect) {
        try {
            Pessoa p = pessoas.criar(form.paraDados());
            redirect.addFlashAttribute("sucesso", p.getNome() + " foi cadastrada.");
            return "redirect:/painel/pessoas";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, null);
        }
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @ModelAttribute("form") FormularioPessoa form, Model model,
                            RedirectAttributes redirect) {
        try {
            Pessoa p = pessoas.atualizar(id, form.paraDados());
            redirect.addFlashAttribute("sucesso", "Dados de " + p.getNome() + " salvos.");
            return "redirect:/painel/pessoas";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, id);
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String nome = pessoas.buscar(id).getNome();
            pessoas.excluir(id);
            redirect.addFlashAttribute("sucesso", nome + " foi excluída.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/pessoas";
    }

    @PostMapping("/{id}/novo-link")
    public String novoLink(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            links.gerarOutro(id);
            redirect.addFlashAttribute("sucesso", "Novo link de " + pessoas.buscar(id).getNome()
                    + " gerado. O link antigo não funciona mais: envie o novo.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/pessoas#pessoa-" + id;
    }

    @PostMapping("/links")
    public String gerarFaltantes(RedirectAttributes redirect) {
        int criados = links.gerarFaltantes();
        redirect.addFlashAttribute("sucesso", criados == 0 ? "Todos os responsáveis já têm link."
                : criados + (criados == 1 ? " link gerado." : " links gerados."));
        return "redirect:/painel/pessoas";
    }

    private String formulario(Model model, FormularioPessoa form, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        model.addAttribute("responsaveis", pessoas.possiveisResponsaveis(id));
        model.addAttribute("temDependentes", id != null && !pessoas.dependentesDe(id).isEmpty());
        if (id != null) {
            Pessoa p = pessoas.buscar(id);
            if (p.entrouTarde(caixinhas.configuracao().getDataFechamentoLista())) {
                model.addAttribute("pessoaTardia", p);
            }
        }
        return "painel/pessoa-form";
    }

    static String descreverPeso(BigDecimal peso) {
        if (peso.compareTo(BigDecimal.ONE) == 0) {
            return "Cota inteira";
        }
        return peso.signum() == 0 ? "Não paga" : "Meia cota";
    }

    static String mensagemDoLink(Pessoa p, List<String> dependentes, String link, String nomeCaixinha) {
        String primeiroNome = p.getNome().split(" ")[0];
        String deQuem = dependentes.isEmpty() ? "a sua cota e os seus depósitos"
                : "a sua cota e os depósitos seus e de " + String.join(" e ", dependentes);
        return "Oi, " + primeiroNome + "! 🎉\n\n"
                + "Este é o seu link da *" + nomeCaixinha + "*:\n" + link + "\n\n"
                + "Por ele você acompanha " + deQuem + ". "
                + "Não precisa de senha: é só guardar o link e não repassar.";
    }
}
