package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.ItemOrcamento;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Painel → Orçamento: itens previstos/reais, meta, cota estimada e "Fixar cota". */
@Controller
@RequestMapping("/painel/orcamento")
public class OrcamentoController {

    private final OrcamentoService orcamento;
    private final CaixinhaService caixinha;
    private final Formato fmt;

    public OrcamentoController(OrcamentoService orcamento, CaixinhaService caixinha, Formato fmt) {
        this.orcamento = orcamento;
        this.caixinha = caixinha;
        this.fmt = fmt;
    }

    /** Campos do item como texto. */
    public static class FormularioItem {
        private Categoria categoria;
        private String descricao = "";
        private String valorPrevisto = "";
        private String valorReal = "";

        DadosItem paraDados() {
            return new DadosItem(categoria, descricao, Dinheiro.ler(valorPrevisto, "Valor previsto"),
                    Dinheiro.ler(valorReal, "Valor real"));
        }

        public Categoria getCategoria() { return categoria; }
        public void setCategoria(Categoria categoria) { this.categoria = categoria; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public String getValorPrevisto() { return valorPrevisto; }
        public void setValorPrevisto(String valorPrevisto) { this.valorPrevisto = valorPrevisto; }
        public String getValorReal() { return valorReal; }
        public void setValorReal(String valorReal) { this.valorReal = valorReal; }
    }

    @GetMapping
    public String pagina(Model model) {
        return pagina(model, new FormularioItem());
    }

    @PostMapping
    public String criar(@ModelAttribute("form") FormularioItem form, Model model, RedirectAttributes redirect) {
        try {
            ItemOrcamento item = orcamento.salvar(null, form.paraDados());
            redirect.addFlashAttribute("sucesso", item.getDescricao() + " adicionado ao orçamento.");
            return "redirect:/painel/orcamento";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, form);
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        ItemOrcamento item = orcamento.buscar(id);
        FormularioItem form = new FormularioItem();
        form.setCategoria(item.getCategoria());
        form.setDescricao(item.getDescricao());
        form.setValorPrevisto(fmt.campo(item.getValorPrevisto()));
        form.setValorReal(fmt.campo(item.getValorReal()));
        return formularioDeEdicao(model, form, id);
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @ModelAttribute("form") FormularioItem form, Model model,
                            RedirectAttributes redirect) {
        try {
            ItemOrcamento item = orcamento.salvar(id, form.paraDados());
            redirect.addFlashAttribute("sucesso", item.getDescricao() + " salvo.");
            return "redirect:/painel/orcamento";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return formularioDeEdicao(model, form, id);
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String descricao = orcamento.buscar(id).getDescricao();
            orcamento.excluir(id);
            redirect.addFlashAttribute("sucesso", descricao + " saiu do orçamento.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/orcamento";
    }

    @PostMapping("/fixar-cota")
    public String fixarCota(RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("sucesso", "Cota fixada em " + fmt.reais(caixinha.fixarCota())
                    + ". A partir de agora ela não muda mais.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/orcamento";
    }

    private String pagina(Model model, FormularioItem form) {
        model.addAttribute("form", form);
        model.addAttribute("itens", orcamento.listar());
        model.addAttribute("categorias", Categoria.values());
        model.addAttribute("cota", caixinha.cota());
        model.addAttribute("resumo", caixinha.resumo());
        return "painel/orcamento";
    }

    private String formularioDeEdicao(Model model, FormularioItem form, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        model.addAttribute("categorias", Categoria.values());
        return "painel/item-form";
    }
}
