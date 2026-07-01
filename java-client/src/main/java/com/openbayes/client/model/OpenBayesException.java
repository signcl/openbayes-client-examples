package com.openbayes.client.model;

/** Raised when an OpenBayes GraphQL call returns errors or an unexpected response. */
public class OpenBayesException extends RuntimeException {

    public OpenBayesException(String message) {
        super(message);
    }

    public OpenBayesException(String message, Throwable cause) {
        super(message, cause);
    }
}
