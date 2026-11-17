package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.link.LinkAcesso;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

/**
 * Página pessoal de cada responsável: /c/{token}. Pública (quem tem o link, vê), sem login.
 * Na fase 2 mostra quem é a pessoa e os dependentes. A situação, o PIX e o extrato chegam na fase 4.
 */
@Controller
public class PaginaPessoalController {

    private final LinkService links;
    private final PessoaService pessoas;
    private final CaixinhaRepository caixinhas;

    public PaginaPessoalController(LinkService links, PessoaService pessoas, CaixinhaRepository caixinhas) {
        this.links = links;
        this.pessoas = pessoas;
        this.caixinhas = caixinhas;
    }

    @GetMapping("/c/{token}")
    public String pagina(@PathVariable String token, Model model, HttpServletResponse resposta) {
        // página com dados pessoais: nada de cache em navegador nem em proxy
        resposta.setHeader("Cache-Control", "no-store");

        Optional<LinkAcesso> link = links.abrir(token);
        if (link.isEmpty()) {
            // mesma resposta para link inexistente e link substituído: não revela nada
            resposta.setStatus(HttpStatus.NOT_FOUND.value());
            return "pessoal/link-invalido";
        }

        Pessoa responsavel = link.get().getPessoa();
        List<Pessoa> dependentes = pessoas.dependentesDe(responsavel.getId());
        model.addAttribute("caixinha", caixinhas.configuracao());
        model.addAttribute("nome", responsavel.getNome());
        model.addAttribute("dependentes", dependentes.stream().map(Pessoa::getNome).toList());
        return "pessoal/inicio";
    }
}
