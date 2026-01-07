/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.ssl.ocsp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.net.InetAddress;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.ssl.ocsp.OcspHttpHandler.OCSP_REQUEST_TYPE;
import static io.netty.handler.ssl.ocsp.OcspHttpHandler.OCSP_RESPONSE_TYPE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;
import static org.bouncycastle.asn1.x509.X509ObjectIdentifiers.id_ad_ocsp;
import static org.bouncycastle.cert.ocsp.CertificateID.HASH_SHA1;

final class OcspClient {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OcspClient.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OCSP_RESPONSE_MAX_SIZE = SystemPropertyUtil.getInt(
            "io.netty.ocsp.responseSize", 1024 * 10);

    static {
        logger.debug("-Dio.netty.ocsp.responseSize: {} bytes", OCSP_RESPONSE_MAX_SIZE);
    }

    /**
     * Query the certificate status using OCSP
     *
     * @param x509Certificate       Client {@link X509Certificate} to validate
     * @param issuer                {@link X509Certificate} issuer of client certificate
     * @param validateResponseNonce Set to {@code true} to enable OCSP response validation
     * @param ioTransport           {@link IoTransport} to use
     * @return {@link Promise} of {@link BasicOCSPResp}
     */
    static Promise<BasicOCSPResp> query(final X509Certificate x509Certificate,
                                        final X509Certificate issuer, final boolean validateResponseNonce,
                                        final IoTransport ioTransport, final DnsNameResolver dnsNameResolver) {
        final EventLoop eventLoop = ioTransport.eventLoop();
        final Promise<BasicOCSPResp> responsePromise = eventLoop.newPromise();
        eventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    CertificateID certificateID = new CertificateID(new JcaDigestCalculatorProviderBuilder()
                            .build().get(HASH_SHA1), new JcaX509CertificateHolder(issuer),
                            x509Certificate.getSerialNumber());

                    // Initialize OCSP Request Builder and add CertificateID into it.
                    OCSPReqBuilder builder = new OCSPReqBuilder();
                    builder.addRequest(certificateID);

                    // Generate 16-bytes (octets) of nonce and add it into OCSP Request builder.
                    // Because as per RFC-8954#2.1:
                    //
                    //   OCSP responders MUST accept lengths of at least
                    //   16 octets and MAY choose to ignore the Nonce extension for requests
                    //   where the length of the nonce is less than 16 octets.
                    byte[] nonce = new byte[16];
                    SECURE_RANDOM.nextBytes(nonce);
                    final DEROctetString derNonce = new DEROctetString(nonce);
                    builder.setRequestExtensions(new Extensions(new Extension(id_pkix_ocsp_nonce, false, derNonce)));

                    // Get OCSP URL from Certificate and query it.
                    URL uri = new URL(parseOcspUrlFromCertificate(x509Certificate));

                    // Find port
                    int port = uri.getPort();
                    if (port == -1) {
                        port = uri.getDefaultPort();
                    }

                    // Configure path
                    String path = uri.getPath();
                    if (path.isEmpty()) {
                        path = "/";
                    } else {
                        if (uri.getQuery() != null) {
                            path = path + '?' + uri.getQuery();
                        }
                    }

                    Promise<OCSPResp> ocspResponsePromise = query(eventLoop,
                            Unpooled.wrappedBuffer(builder.build().getEncoded()),
                            uri.getHost(), port, path, ioTransport, dnsNameResolver);

                    // Validate OCSP response
                    ocspResponsePromise.addListener((GenericFutureListener<Future<OCSPResp>>) future -> {
                        // If Future was successful then we have received OCSP response
                        // We will now validate it.
                        if (future.isSuccess()) {
                            try {
                                BasicOCSPResp resp = (BasicOCSPResp) future.getNow().getResponseObject();
                                validateResponse(responsePromise, resp, derNonce, issuer, validateResponseNonce);
                            } catch (Throwable t) {
                                responsePromise.tryFailure(t);
                            }
                        } else {
                            responsePromise.tryFailure(future.cause());
                        }
                    });

                } catch (Exception ex) {
                    responsePromise.tryFailure(ex);
                }
            }
        });
        return responsePromise;
    }

    /**
     * Query the OCSP responder for certificate status using HTTP/1.1
     *
     * @param eventLoop   {@link EventLoop} for HTTP request execution
     * @param ocspRequest {@link ByteBuf} containing OCSP request data
     * @param host        OCSP responder hostname
     * @param port        OCSP responder port
     * @param path        OCSP responder path
     * @param ioTransport {@link IoTransport} to use
     * @return Returns {@link Promise} containing {@link OCSPResp}
     */
    private static Promise<OCSPResp> query(final EventLoop eventLoop, final ByteBuf ocspRequest,
                                           final String host, final int port, final String path,
                                           final IoTransport ioTransport, final DnsNameResolver dnsNameResolver) {
        final Promise<OCSPResp> responsePromise = eventLoop.newPromise();

        try {
            final Bootstrap bootstrap = new Bootstrap()
                    .group(ioTransport.eventLoop())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .channelFactory(ioTransport.socketChannel())
                    .attr(OcspServerCertificateValidator.OCSP_PIPELINE_ATTRIBUTE, Boolean.TRUE)
                    .handler(new Initializer(responsePromise));
            dnsNameResolver.resolve(host).addListener((FutureListener<InetAddress>) future -> {

                // If Future was successful then we have successfully resolved OCSP server address.
                // If not, mark 'responsePromise' as failure.
                if (future.isSuccess()) {
                    // Get the resolved InetAddress
                    InetAddress hostAddress = future.getNow();
                    final ChannelFuture channelFuture = bootstrap.connect(hostAddress, port);
                    channelFuture.addListener(f -> {
                        // If Future was successful then connection to OCSP responder was successful.
                        // We will send a OCSP request now
                        if (f.isSuccess()) {
                            FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, path,
                                    ocspRequest);
                            request.headers().add(HttpHeaderNames.HOST, host);
                            request.headers().add(HttpHeaderNames.USER_AGENT, "Netty OCSP Client");
                            request.headers().add(HttpHeaderNames.CONTENT_TYPE, OCSP_REQUEST_TYPE);
                            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, OCSP_RESPONSE_TYPE);
                            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, ocspRequest.readableBytes());

                            // Send the OCSP HTTP Request
                            channelFuture.channel().writeAndFlush(request);
                        } else {
                            responsePromise.tryFailure(new IllegalStateException(
                                    "Connection to OCSP Responder Failed", f.cause()));
                        }
                    });
                } else {
                    responsePromise.tryFailure(future.cause());
                }
            });
        } catch (Exception ex) {
            responsePromise.tryFailure(ex);
        }

        return responsePromise;
    }

    private static void validateResponse(Promise<BasicOCSPResp> responsePromise, BasicOCSPResp basicResponse,
                                         DEROctetString derNonce, X509Certificate issuer, boolean validateNonce) {
        try {
            // Validate number of responses. We only requested for 1 certificate
            // so number of responses must be 1. If not, we will throw an error.
            int responses = basicResponse.getResponses().length;
            if (responses != 1) {
                throw new IllegalArgumentException("Expected number of responses was 1 but got: " + responses);
            }

            if (validateNonce) {
                validateNonce(basicResponse, derNonce);
            }
            validateSignature(basicResponse, issuer);
            responsePromise.trySuccess(basicResponse);
        } catch (Exception ex) {
            responsePromise.tryFailure(ex);
        }
    }

    /**
     * Validate OCSP response nonce
     */
    private static void validateNonce(BasicOCSPResp basicResponse, DEROctetString encodedNonce) throws OCSPException {
        Extension nonceExt = basicResponse.getExtension(id_pkix_ocsp_nonce);
        if (nonceExt != null) {
            DEROctetString responseNonceString = (DEROctetString) nonceExt.getExtnValue();
            if (!responseNonceString.equals(encodedNonce)) {
                throw new OCSPException("Nonce does not match");
            }
        } else {
            throw new IllegalArgumentException("Nonce is not present");
        }
    }

    /**
     * Validate OCSP response signature
     */
    static void validateSignature(BasicOCSPResp resp, X509Certificate issuerCertificate) throws OCSPException {
        try {
            X509CertificateHolder[] certs = resp.getCerts();
            JcaContentVerifierProviderBuilder providerBuilder = new JcaContentVerifierProviderBuilder();

            // If responder certificate is included, validate the chain
            if (certs != null && certs.length > 0) {

                // Use the first included certificate to verify the OCSP response signature.
                X509CertificateHolder responderCert = certs[0];

                // Verify OCSP response signature using responder cert
                ContentVerifierProvider responderVerifier = providerBuilder.build(responderCert);

                if (!resp.isSignatureValid(responderVerifier)) {
                    throw new OCSPException("OCSP response signature is not valid");
                }

                // Build chain from responder certificate to issuer using CertPathBuilder
                validateCertificateChain(responderCert, certs, issuerCertificate);
            } else {
                // Validate signature using issuer certificate
                ContentVerifierProvider issuerVerifier = providerBuilder.build(issuerCertificate);

                if (!resp.isSignatureValid(issuerVerifier)) {
                    throw new OCSPException("OCSP response signature is not valid");
                }
            }
        } catch (OperatorCreationException e) {
            throw new OCSPException("Error validating OCSP-Signature", e);
        } catch (CertificateException e) {
            throw new OCSPException("Error while processing certificates for OCSP signature validation", e);
        }
    }

    /**
     * Validates that a certificate chain can be built from the responder certificate to the issuer.
     * Uses Java's CertPathBuilder to construct and validate the chain.
     */
    private static void validateCertificateChain(X509CertificateHolder responderCert,
                                                   X509CertificateHolder[] allCerts,
                                                   X509Certificate issuerCertificate) throws OCSPException {
        try {
            // Convert BouncyCastle certificate holders to Java X509Certificates
            List<X509Certificate> certList = new ArrayList<>(allCerts.length);
            for (X509CertificateHolder certHolder : allCerts) {
                certList.add(new JcaX509CertificateConverter().getCertificate(certHolder));
            }

            // Create a CertStore with all the certificates from the OCSP response
            CertStore certStore = CertStore.getInstance("Collection",
                    new CollectionCertStoreParameters(certList));

            // Set up the target certificate selector for the responder certificate
            X509CertSelector targetConstraints = new X509CertSelector();
            targetConstraints.setCertificate(new JcaX509CertificateConverter().getCertificate(responderCert));

            // Set up trust anchor with the issuer certificate
            TrustAnchor trustAnchor = new TrustAnchor(issuerCertificate, null);

            // Build PKIX parameters
            PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(
                    Collections.singleton(trustAnchor), targetConstraints);
            pkixParams.addCertStore(certStore);
            pkixParams.setRevocationEnabled(false); // Don't check revocation when validating OCSP response

            // Build and validate the certificate path
            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            builder.build(pkixParams);

            // If we reach here, the chain is valid
        } catch (CertPathBuilderException e) {
            throw new OCSPException("OCSP responder certificate is not trusted by issuer: " + e.getMessage(), e);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new OCSPException("Error setting up certificate path validation", e);
        } catch (CertificateException e) {
            throw new OCSPException("Error converting certificates for path validation", e);
        }
    }

    /**
     * Parse OCSP endpoint URL from Certificate
     *
     * @param cert Certificate to be parsed
     * @return OCSP endpoint URL
     * @throws NullPointerException     If we couldn't locate OCSP responder URL
     * @throws IllegalArgumentException If we couldn't parse X509Certificate into JcaX509CertificateHolder
     */
    private static String parseOcspUrlFromCertificate(X509Certificate cert) {
        X509CertificateHolder holder;
        try {
            holder = new JcaX509CertificateHolder(cert);
        } catch (CertificateEncodingException e) {
            // Though this should never happen
            throw new IllegalArgumentException("Error while parsing X509Certificate into JcaX509CertificateHolder", e);
        }

        AuthorityInformationAccess aiaExtension = AuthorityInformationAccess.fromExtensions(holder.getExtensions());

        // Lookup for OCSP responder url
        for (AccessDescription accessDescription : aiaExtension.getAccessDescriptions()) {
            if (accessDescription.getAccessMethod().equals(id_ad_ocsp)) {
                return accessDescription.getAccessLocation().getName().toASN1Primitive().toString();
            }
        }

        throw new NullPointerException("Unable to find OCSP responder URL in Certificate");
    }

    static final class Initializer extends ChannelInitializer<SocketChannel> {

        private final Promise<OCSPResp> responsePromise;

        Initializer(Promise<OCSPResp> responsePromise) {
            this.responsePromise = checkNotNull(responsePromise, "ResponsePromise");
        }

        @Override
        protected void initChannel(SocketChannel socketChannel) {
            ChannelPipeline pipeline = socketChannel.pipeline();
            pipeline.addLast(new HttpClientCodec());
            pipeline.addLast(new HttpObjectAggregator(OCSP_RESPONSE_MAX_SIZE));
            pipeline.addLast(new OcspHttpHandler(responsePromise));
        }
    }

    private OcspClient() {
        // Prevent outside initialization
    }
}
