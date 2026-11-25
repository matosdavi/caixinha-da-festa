package com.natal.caixinha.web;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.cronograma.CronogramaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Painel → Cronograma: data da festa, etapas e a verificação do pagamento do lugar. */
@Controller
@RequestMapping("/painel/cronograma")
public class CronogramaController {

    private final CronogramaService cronograma;

    public CronogramaController(CronogramaService cronograma) {
        this.cronograma = cronograma;
    }

    @GetMapping
    public String tela(Model model) {
        model.addAttribute("visao", cronograma.visao());
        return "painel/cronograma";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String dataFesta, RedirectAttributes redirect) {
        try {
            LocalDate data = lerData(dataFesta);
            cronograma.definirDataFesta(data);
            redirect.addFlashAttribute("sucesso",
                    data == null ? "Data da festa apagada: em votação." : "Data da festa salva.");
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel/cronograma";
    }

    private static LocalDate lerData(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new RegraException("Data inválida.");
        }
    }
}
