/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.ocsp;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.CharsetUtil;

/**
 * ATTENTION: This is an incomplete example! In order to provide a fully functional
 * end-to-end example we'd need an X.509 certificate and the matching PrivateKey.
 */
@SuppressWarnings("unused")
public class OcspServerExample {
    public static void main(String[] args) throws Exception {
        // We assume there's a private key.
        PrivateKey privateKey = null;

        // Step 1: Load the certificate chain for netty.io. We'll need the certificate
        // and the issuer's certificate and we don't need any of the intermediate certs.
        // The array is assumed to be a certain order to keep things simple.
        X509Certificate[] keyCertChain = parseCertificates(OcspServerExample.class, "netty_io_chain.pem");

        X509Certificate certificate = keyCertChain[0];
        X509Certificate issuer = keyCertChain[keyCertChain.length - 1];

        // Step 2: We need the URL of the CA's OCSP responder server. It's somewhere encoded
        // into the certificate! Notice that it's an HTTP URL.
        URI uri = OcspUtils.ocspUri(certificate);
        System.out.println("OCSP Responder URI: " + uri);

        if (uri == null) {
            throw new IllegalStateException("The CA/certificate doesn't have an OCSP responder");
        }

        // Step 3: Construct the OCSP request
        OCSPReq request = new OcspRequestBuilder()
                .certificate(certificate)
                .issuer(issuer)
                .build();

        // Step 4: Do the request to the CA's OCSP responder
        OCSPResp response = OcspUtils.request(uri, request, 5L, TimeUnit.SECONDS);
        if (response.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
            throw new IllegalStateException("response-status=" + response.getStatus());
        }

        // Step 5: Is my certificate any good or has the CA revoked it?
        BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
        SingleResp first = basicResponse.getResponses()[0];

        CertificateStatus status = first.getCertStatus();
        System.out.println("Status: " + (status == CertificateStatus.GOOD ? "Good" : status));
        System.out.println("This Update: " + first.getThisUpdate());
        System.out.println("Next Update: " + first.getNextUpdate());

        if (status != null) {
            throw new IllegalStateException("certificate-status=" + status);
        }

        BigInteger certSerial = certificate.getSerialNumber();
        BigInteger ocspSerial = first.getCertID().getSerialNumber();
        if (!certSerial.equals(ocspSerial)) {
            throw new IllegalStateException("Bad Serials=" + certSerial + " vs. " + ocspSerial);
        }

        // Step 6: Cache the OCSP response and use it as long as it's not
        // expired. The exact semantics are beyond the scope of this example.

        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException("OpenSSL is not available!");
        }

        if (!OpenSsl.isOcspSupported()) {
            throw new IllegalStateException("OCSP is not supported!");
        }

        if (privateKey == null) {
            throw new IllegalStateException("Because we don't have a PrivateKey we can't continue past this point.");
        }

        ReferenceCountedOpenSslContext context
            = (ReferenceCountedOpenSslContext) SslContextBuilder.forServer(privateKey, keyCertChain)
                .sslProvider(SslProvider.OPENSSL)
                .enableOcsp(true)
                .build();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .childHandler(newServerHandler(context, response));

            // so on and so forth...
        } finally {
            context.release();
        }
    }

    private static ChannelInitializer<Channel> newServerHandler(final ReferenceCountedOpenSslContext context,
            final OCSPResp response) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                SslHandler sslHandler = context.newHandler(ch.alloc());

                if (response != null) {
                    ReferenceCountedOpenSslEngine engine
                        = (ReferenceCountedOpenSslEngine) sslHandler.engine();

                    engine.setOcspResponse(response.getEncoded());
                }

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(sslHandler);

                // so on and so forth...
            }
        };
    }

    private static X509Certificate[] parseCertificates(Class<?> clazz, String name) throws Exception {
        InputStream in = clazz.getResourceAsStream(name);
        if (in == null) {
            throw new FileNotFoundException("clazz=" + clazz + ", name=" + name);
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, CharsetUtil.US_ASCII));
            try {
                return parseCertificates(reader);
            } finally {
                reader.close();
            }
        } finally {
            in.close();
        }
    }

    private static X509Certificate[] parseCertificates(Reader reader) throws Exception {

        JcaX509CertificateConverter converter = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider());

        List<X509Certificate> dst = new ArrayList<X509Certificate>();

        PEMParser parser = new PEMParser(reader);
        try {
          X509CertificateHolder holder = null;

          while ((holder = (X509CertificateHolder) parser.readObject()) != null) {
            X509Certificate certificate = converter.getCertificate(holder);
            if (certificate == null) {
              continue;
            }

            dst.add(certificate);
          }
        } finally {
            parser.close();
        }

        return dst.toArray(new X509Certificate[0]);
    }
}
