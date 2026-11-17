package com.natal.caixinha.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinkWhatsAppTest {

    @Test
    void comTelefoneAbreAConversaDaPessoa() {
        assertEquals("https://wa.me/5511999998888?text=Oi%2C%20Ana%21%20*negrito*%20%F0%9F%8E%89",
                LinkWhatsApp.para("5511999998888", "Oi, Ana! *negrito* 🎉"));
    }

    @Test
    void semTelefoneDeixaEscolherOContato() {
        assertTrue(LinkWhatsApp.para(null, "oi").startsWith("https://wa.me/?text="));
        assertTrue(LinkWhatsApp.para("", "oi").startsWith("https://wa.me/?text="));
    }

    @Test
    void quebraDeLinhaECaracteresDeUrl() {
        assertEquals("https://wa.me/?text=linha%201%0Ahttps%3A%2F%2Fx.com%2Fc%2Fa%2Bb",
                LinkWhatsApp.para(null, "linha 1\nhttps://x.com/c/a+b"));
    }
}
