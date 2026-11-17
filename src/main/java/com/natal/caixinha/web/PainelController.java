package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Painel do organizador (protegido por login): início com saldo, progresso e atrasos. */
@Controller
public class PainelController {

    private final CaixinhaService caixinha;

    public PainelController(CaixinhaService caixinha) {
        this.caixinha = caixinha;
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/painel";
    }

    @GetMapping("/entrar")
    public String entrar() {
        return "entrar";
    }

    @GetMapping("/painel")
    public String inicio(Model model) {
        model.addAttribute("resumo", caixinha.resumo());
        return "painel/inicio";
    }
}
