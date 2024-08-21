package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class GeneralNames implements DerWriter.WritableSequence {
    private final Collection<GeneralName> names;

    private GeneralNames(Collection<GeneralName> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Names cannot be empty");
        }
        this.names = names;
    }

    public GeneralNames(GeneralName name) {
        names = Collections.singletonList(Objects.requireNonNull(name, "name"));
    }

    public void writeTo(DerWriter writer) {
        writer.writeSequence(this);
    }

    public void writeTo(int tag, DerWriter writer) {
        writer.writeSequence(tag, this);
    }

    public static byte[] generalNames(Collection<GeneralName> names) {
        try (DerWriter der = new DerWriter()) {
            new GeneralNames(names).writeTo(der);
            return der.getBytes();
        }
    }

    @Override
    public void writeSequence(DerWriter writer) {
        names.forEach(n -> n.writeTo(writer));
    }
}
