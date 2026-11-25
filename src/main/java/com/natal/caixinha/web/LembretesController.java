package com.natal.caixinha.web;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lembrete.LembreteService;
import com.natal.caixinha.lembrete.LembreteService.LinhaIndividual;
import com.natal.caixinha.whatsapp.LinkWhatsApp;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Toque em "Enviar no WhatsApp" de um lembrete: grava o aviso do mês e abre o WhatsApp com o texto pronto.
 * O texto e o telefone são recalculados aqui; o formulário só diz qual mês e qual pessoa.
 */
@Controller
@RequestMapping("/painel/lembretes")
public class LembretesController {

    private final LembreteService lembretes;

    public LembretesController(LembreteService lembretes) {
        this.lembretes = lembretes;
    }

    @PostMapping("/grupo")
    public String grupo(@RequestParam(required = false) String mes, RedirectAttributes redirect) {
        try {
            return "redirect:" + LinkWhatsApp.para(null, lembretes.avisarGrupo(lerMes(mes)));
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/painel";
        }
    }

    @PostMapping("/individual")
    public String individual(@RequestParam(required = false) String mes,
                             @RequestParam(required = false) Long pessoaId, RedirectAttributes redirect) {
        try {
            LinhaIndividual linha = lembretes.avisarIndividual(lerMes(mes), pessoaId, Enderecos.base());
            return "redirect:" + LinkWhatsApp.para(linha.telefone(), linha.texto());
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/painel";
        }
    }

    @PostMapping("/desfazer")
    public String desfazer(@RequestParam(required = false) String mes,
                           @RequestParam(required = false) Long pessoaId, RedirectAttributes redirect) {
        try {
            lembretes.desfazer(lerMes(mes), pessoaId);
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel";
    }

    private static YearMonth lerMes(String mes) {
        try {
            return YearMonth.parse(mes == null ? "" : mes.trim());
        } catch (DateTimeParseException e) {
            throw new RegraException("Este lembrete não está mais disponível.");
        }
    }
}
