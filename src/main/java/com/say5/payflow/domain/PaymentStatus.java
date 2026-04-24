package com.say5.payflow.domain;

public enum PaymentStatus {
    REQUIRES_CONFIRMATION("requires_confirmation"),
    PROCESSING("processing"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELED("canceled");

    private final String wire;

    PaymentStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PaymentStatus fromWire(String s) {
        for (PaymentStatus v : values()) {
            if (v.wire.equals(s)) return v;
        }
        throw new IllegalArgumentException("unknown payment status: " + s);
    }

    /** Terminal statuses don't transition further. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
