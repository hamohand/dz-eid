package com.muhend.dzeid.core.tlv;

/** Structure TLV invalide ou tronquée. */
public class TlvException extends RuntimeException {

    public TlvException(String message) {
        super(message);
    }
}
