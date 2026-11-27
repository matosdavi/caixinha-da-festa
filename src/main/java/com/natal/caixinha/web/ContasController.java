package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.prestacao.OpcaoSobra;
import com.natal.caixinha.prestacao.PrestacaoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/** Painel → Contas: prestação de contas, escolhas, lançamento das partes e CSV dos créditos (seção 3.7). */
@Controller
@RequestMapping("/painel/contas")
public class ContasController {

    private final PrestacaoService prestacao;
    private final CaixinhaRepository caixinhas;

    public ContasController(PrestacaoService prestacao, CaixinhaRepository caixinhas) {
        this.prestacao = prestacao;
        this.caixinhas = caixinhas;
    }

    @GetMapping
    public String tela(Model model) {
        model.addAttribute("v", prestacao.visao());
        model.addAttribute("anoSeguinte", anoSeguinte());
        return "painel/contas";
    }

    @PostMapping("/{id}/escolha")
    public String escolha(@PathVariable Long id, @RequestParam(required = false) OpcaoSobra opcao,
                          RedirectAttributes redirect) {
        try {
            if (opcao == null) {
                throw new RegraException("Escolha devolver ou crédito.");
            }
            prestacao.definirEscolha(id, opcao);
            redirect.addFlashAttribute("sucesso", "Escolha atualizada.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/contas";
    }

    @PostMapping("/{id}/lancar")
    public String lancar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            prestacao.lancar(id);
            var linha = prestacao.visao().linhas().stream().filter(l -> l.pessoa().getId().equals(id)).findFirst()
                    .orElseThrow();
            redirect.addFlashAttribute("sucesso", "Lançado: " + Dinheiro.formatar(linha.parte()) + " de "
                    + linha.pessoa().getNome() + " (" + (linha.efetiva() == OpcaoSobra.DEVOLVER ? "Sobra da festa"
                    : "Crédito para " + anoSeguinte()) + ").");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/contas";
    }

    @GetMapping("/creditos.csv")
    public ResponseEntity<byte[]> creditos() {
        // BOM para o Excel reconhecer o UTF-8 (acentos dos nomes)
        byte[] corpo = ("﻿" + prestacao.csvCreditos()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"creditos-" + anoSeguinte() + ".csv\"")
                .body(corpo);
    }

    private int anoSeguinte() {
        return caixinhas.configuracao().getMesFim().getYear() + 1;
    }
}
