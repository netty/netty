package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

import java.util.Collection;

public final class CrlDistributionPoints {
    private CrlDistributionPoints() {
    }

    public static byte[] distributionPoints(Collection<DistributionPoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Points cannot be empty");
        }
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> {
                points.forEach(p -> p.writeTo(w));
            }).getBytes();
        }
    }
}
