package com.natal.caixinha.web;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.desistencia.DesistenciaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Painel → Pessoas → Desistência: devolução sugerida e saída da caixinha. */
@Controller
@RequestMapping("/painel/pessoas/{id}/desistencia")
public class DesistenciaController {

    private final DesistenciaService desistencias;
    private final Clock clock;

    public DesistenciaController(DesistenciaService desistencias, Clock clock) {
        this.desistencias = desistencias;
        this.clock = clock;
    }

    @GetMapping
    public String tela(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean vaga, Model model) {
        model.addAttribute("s", desistencias.sugerir(id, vaga));
        model.addAttribute("hoje", LocalDate.now(clock));
        return "painel/desistencia";
    }

    @PostMapping
    public String confirmar(@PathVariable Long id, @RequestParam(required = false) String valor,
                            @RequestParam(required = false) String data, RedirectAttributes redirect) {
        try {
            BigDecimal devolucao = Dinheiro.ler(valor, "Devolução");
            desistencias.confirmar(id, devolucao == null ? BigDecimal.ZERO : devolucao, lerData(data));
            redirect.addFlashAttribute("sucesso", "Desistência registrada: devolução de "
                    + Dinheiro.formatar(devolucao == null ? BigDecimal.ZERO : devolucao) + ".");
            return "redirect:/painel/pessoas";
        } catch (RegraException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/painel/pessoas/" + id + "/desistencia";
        }
    }

    private LocalDate lerData(String data) {
        if (data == null || data.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(data.trim());
        } catch (DateTimeParseException e) {
            throw new RegraException("Data inválida.");
        }
    }
}
