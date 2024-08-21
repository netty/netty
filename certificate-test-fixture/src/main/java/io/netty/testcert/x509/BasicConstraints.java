package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

public final class BasicConstraints {
    private BasicConstraints() {
    }

    public static byte[] withPathLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> w.writeBoolean(true).writeInteger(length)).getBytes();
        }
    }

    public static byte[] isCa(boolean isCa) {
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> w.writeBoolean(isCa)).getBytes();
        }
    }
}
