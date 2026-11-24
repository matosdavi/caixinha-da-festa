package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.pix.BrCode;
import com.natal.caixinha.pix.ChavePix;
import com.natal.caixinha.pix.QrCodeSvg;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Painel → Configurações: chave PIX e recebedor, com um PIX de teste de R$ 1,00. */
@Controller
@RequestMapping("/painel/configuracoes")
public class ConfiguracoesController {

    private static final BigDecimal VALOR_DE_TESTE = new BigDecimal("1.00");

    private final CaixinhaService caixinha;

    public ConfiguracoesController(CaixinhaService caixinha) {
        this.caixinha = caixinha;
    }

    @GetMapping
    public String formulario(Model model) {
        var r = caixinha.recebedor();
        return pagina(model, r.map(x -> ChavePix.tipoDe(x.chave())).orElse(null), r.map(BrCode.Recebedor::chave).orElse(""),
                r.map(BrCode.Recebedor::nome).orElse(""), r.map(BrCode.Recebedor::cidade).orElse(""));
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) ChavePix.Tipo tipo,
                         @RequestParam(required = false) String chave,
                         @RequestParam(required = false) String nome,
                         @RequestParam(required = false) String cidade,
                         Model model, RedirectAttributes redirect) {
        try {
            caixinha.salvarPix(tipo, chave, nome, cidade);
            redirect.addFlashAttribute("sucesso", "Configurações salvas.");
            return "redirect:/painel/configuracoes";
        } catch (RegraException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, tipo, chave, nome, cidade);
        }
    }

    private String pagina(Model model, ChavePix.Tipo tipo, String chave, String nome, String cidade) {
        model.addAttribute("tipos", ChavePix.Tipo.values());
        model.addAttribute("tipo", tipo);
        model.addAttribute("chave", chave == null ? "" : chave);
        model.addAttribute("nome", nome == null ? "" : nome);
        model.addAttribute("cidade", cidade == null ? "" : cidade);
        model.addAttribute("nomeNoPix", BrCode.limpar(nome, BrCode.MAX_NOME));
        model.addAttribute("cidadeNoPix", BrCode.limpar(cidade, BrCode.MAX_CIDADE));
        caixinha.recebedor().ifPresent(r -> {
            String pix = BrCode.gerar(r, VALOR_DE_TESTE, "TESTE");
            model.addAttribute("pixDeTeste", pix);
            model.addAttribute("qrDeTeste", QrCodeSvg.svg(pix));
        });
        return "painel/configuracoes";
    }
}
