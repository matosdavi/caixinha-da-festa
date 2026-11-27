package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.DescontoNovembroService;
import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Painel → Desconto de novembro (seção 3.4): prévia, confirmar e desfazer (só em outubro). */
@Controller
@RequestMapping("/painel/desconto-novembro")
public class DescontoNovembroController {

    private final DescontoNovembroService descontos;

    public DescontoNovembroController(DescontoNovembroService descontos) {
        this.descontos = descontos;
    }

    @GetMapping
    public String tela(Model model) {
        DescontoNovembroService.Previa p = descontos.previa();
        model.addAttribute("p", p);
        model.addAttribute("meia", p.descontoPorPeso().divide(BigDecimal.valueOf(2)));
        return "painel/desconto-novembro";
    }

    @PostMapping
    public String confirmar(RedirectAttributes redirect) {
        try {
            BigDecimal d = descontos.confirmar();
            redirect.addFlashAttribute("sucesso", "Desconto de novembro confirmado: " + Dinheiro.formatar(d)
                    + " por cota inteira.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/desconto-novembro";
    }

    @PostMapping("/desfazer")
    public String desfazer(RedirectAttributes redirect) {
        try {
            descontos.desfazer();
            redirect.addFlashAttribute("sucesso", "Desconto de novembro desfeito.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/desconto-novembro";
    }
}
