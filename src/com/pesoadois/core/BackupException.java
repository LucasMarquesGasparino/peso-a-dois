package com.pesoadois.core;

/** Falha ao ler um backup. A mensagem já está em português, pronta para o usuário. */
public final class BackupException extends Exception {
    private static final long serialVersionUID = 1L;

    public BackupException(String message) {
        super(message);
    }
}
