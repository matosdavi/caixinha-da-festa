package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Painel do organizador (protegido por login). Por enquanto, só o início. */
@Controller
public class PainelController {

    private final CaixinhaRepository caixinhas;
    private final PessoaRepository pessoas;

    public PainelController(CaixinhaRepository caixinhas, PessoaRepository pessoas) {
        this.caixinhas = caixinhas;
        this.pessoas = pessoas;
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
        model.addAttribute("caixinha", caixinhas.configuracao());
        model.addAttribute("pessoasAtivas", pessoas.findByAtivoTrueOrderByNomeAsc().size());
        return "painel/inicio";
    }
}
