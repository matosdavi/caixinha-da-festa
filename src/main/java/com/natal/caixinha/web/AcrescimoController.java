package com.natal.caixinha.web;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.pessoa.PessoaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Acréscimo do anfitrião para quem entrou depois do fechamento da lista (do Início ou do formulário da pessoa). */
@Controller
public class AcrescimoController {

    private final PessoaService pessoas;

    public AcrescimoController(PessoaService pessoas) {
        this.pessoas = pessoas;
    }

    @PostMapping("/painel/pessoas/{id}/acrescimo")
    public String salvar(@PathVariable Long id, @RequestParam(required = false) String acrescimo,
                         @RequestParam(required = false) String voltar, RedirectAttributes redirect) {
        String destino = "inicio".equals(voltar) ? "redirect:/painel" : "redirect:/painel/pessoas/" + id + "/editar";
        try {
            BigDecimal valor = Dinheiro.ler(acrescimo, "Acréscimo");
            pessoas.definirAcrescimo(id, valor);
            redirect.addFlashAttribute("sucesso", "Acréscimo de " + pessoas.buscar(id).getNome() + " salvo: "
                    + Dinheiro.formatar(valor) + ".");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return destino;
    }
}
