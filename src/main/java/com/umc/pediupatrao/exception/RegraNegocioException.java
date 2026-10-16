package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(String mensagem) {
        this(mensagem, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public RegraNegocioException(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }

    public static RegraNegocioException conflito(String mensagem) {
        return new RegraNegocioException(mensagem, HttpStatus.CONFLICT);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
