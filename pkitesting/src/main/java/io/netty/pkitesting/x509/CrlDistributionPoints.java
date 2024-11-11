/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.pkitesting.x509;

import io.netty.util.internal.UnstableApi;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;

@UnstableApi
public final class CrlDistributionPoints {
    private CrlDistributionPoints() {
    }

    public static byte[] distributionPoints(Collection<DistributionPoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Points cannot be empty");
        }
        org.bouncycastle.asn1.x509.DistributionPoint[] bcPoints =
                new org.bouncycastle.asn1.x509.DistributionPoint[points.size()];
        int i = 0;
        for (DistributionPoint point : points) {
            bcPoints[i] = new org.bouncycastle.asn1.x509.DistributionPoint(
                    point.fullName == null ? null : new DistributionPointName(
                            new GeneralNames(GeneralName.getInstance(point.fullName.getEncoded()))),
                    null,
                    point.issuer == null ? null : new GeneralNames(GeneralName.getInstance(point.issuer.getEncoded()))
            );
            i++;
        }
        try {
            return new CRLDistPoint(bcPoints).getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
