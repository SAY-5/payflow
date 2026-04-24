package com.say5.payflow.domain;

public enum RefundStatus {
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private final String wire;

    RefundStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static RefundStatus fromWire(String s) {
        for (RefundStatus v : values()) if (v.wire.equals(s)) return v;
        throw new IllegalArgumentException("unknown refund status: " + s);
    }
}
