package ch.sbb.polarion.extension.aad.synchronizer.utils;

public class OAuth2Exception extends RuntimeException {

    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
