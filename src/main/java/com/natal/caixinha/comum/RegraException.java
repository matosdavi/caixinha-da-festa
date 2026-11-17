package com.natal.caixinha.comum;

/** Regra de negócio violada. A mensagem é mostrada ao organizador como está. */
public class RegraException extends RuntimeException {

    public RegraException(String mensagem) {
        super(mensagem);
    }
}
