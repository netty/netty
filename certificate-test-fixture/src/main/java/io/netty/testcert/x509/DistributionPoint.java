package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

import java.util.Objects;

public final class DistributionPoint implements DerWriter.WritableSequence {
    final GeneralName fullName;
    final GeneralName issuer;

    public DistributionPoint(GeneralName fullName, GeneralName issuer) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.issuer = issuer;
    }

    public void writeTo(DerWriter writer) {
        writer.writeSequence(this);
    }

    @Override
    public void writeSequence(DerWriter writer) {
        GeneralNames fullNames = new GeneralNames(fullName);
        writer.writeExplicit(DerWriter.TAG_CONTEXT,
                w -> fullNames.writeTo(DerWriter.TAG_CONSTRUCTED|DerWriter.TAG_CONTEXT, w));
        if (issuer != null) {
            GeneralNames issuerNames = new GeneralNames(issuer);
            writer.writeExplicit(DerWriter.TAG_CONTEXT|2,
                    w -> issuerNames.writeTo(w));
        }
    }
}
