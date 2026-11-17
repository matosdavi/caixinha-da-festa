package com.natal.caixinha.whatsapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Mensagem de WhatsApp "em 1 toque": o link abre o WhatsApp com o texto pronto e
 * o organizador só toca em enviar. Sem API, sem custo.
 */
public final class LinkWhatsApp {

    private LinkWhatsApp() {
    }

    /** Com telefone, abre a conversa da pessoa. Sem telefone, o WhatsApp pede para escolher o contato. */
    public static String para(String telefoneE164, String texto) {
        String destino = telefoneE164 == null || telefoneE164.isBlank() ? "" : telefoneE164;
        return "https://wa.me/" + destino + "?text=" + codificar(texto);
    }

    static String codificar(String texto) {
        // URLEncoder usa '+' para espaço (formulário); em URL de texto o certo é %20.
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
