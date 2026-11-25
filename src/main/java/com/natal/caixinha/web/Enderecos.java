package com.natal.caixinha.web;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Endereços absolutos do app, para mandar links por WhatsApp. */
final class Enderecos {

    private Enderecos() {
    }

    /** https://caixinha-da-festa.onrender.com (o Render repassa o https nos cabeçalhos X-Forwarded-*). */
    static String base() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
