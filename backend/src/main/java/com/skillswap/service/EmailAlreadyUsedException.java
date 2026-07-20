package com.skillswap.service;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) { super(message); }
}
