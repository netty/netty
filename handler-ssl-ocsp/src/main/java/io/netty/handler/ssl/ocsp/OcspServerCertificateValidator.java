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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link OcspServerCertificateValidator} validates incoming server's certificate
 * using OCSP. Once TLS handshake is completed, {@link SslHandshakeCompletionEvent#SUCCESS} is fired, validator
 * will perform certificate validation using OCSP over HTTP/1.1 with the server's certificate issuer OCSP responder.
 */
public class OcspServerCertificateValidator extends ChannelInboundHandlerAdapter {

    private final boolean closeAndThrowIfNotValid;
    private final boolean validateNonce;
    private final IoTransport ioTransport;
    private final DnsNameResolver dnsNameResolver;

    /**
     * Create a new {@link OcspServerCertificateValidator} instance without nonce validation
     * on OCSP response, using default {@link IoTransport#DEFAULT} instance,
     * default {@link DnsNameResolver} implementation and with {@link #closeAndThrowIfNotValid}
     * set to {@code true}
     */
    public OcspServerCertificateValidator() {
        this(false);
    }

    /**
     * Create a new {@link OcspServerCertificateValidator} instance with
     * default {@link IoTransport#DEFAULT} instance and default {@link DnsNameResolver} implementation
     * and {@link #closeAndThrowIfNotValid} set to {@code true}.
     *
     * @param validateNonce Set to {@code true} if we should force nonce validation on
     *                      OCSP response else set to {@code false}
     */
    public OcspServerCertificateValidator(boolean validateNonce) {
        this(validateNonce, IoTransport.DEFAULT);
    }

    /**
     * Create a new {@link OcspServerCertificateValidator} instance
     *
     * @param validateNonce Set to {@code true} if we should force nonce validation on
     *                      OCSP response else set to {@code false}
     * @param ioTransport   {@link IoTransport} to use
     */
    public OcspServerCertificateValidator(boolean validateNonce, IoTransport ioTransport) {
        this(validateNonce, ioTransport, createDefaultResolver(ioTransport));
    }

    /**
     * Create a new {@link IoTransport} instance with {@link #closeAndThrowIfNotValid} set to {@code true}
     *
     * @param validateNonce   Set to {@code true} if we should force nonce validation on
     *                        OCSP response else set to {@code false}
     * @param ioTransport     {@link IoTransport} to use
     * @param dnsNameResolver {@link DnsNameResolver} implementation to use
     */
    public OcspServerCertificateValidator(boolean validateNonce, IoTransport ioTransport,
                                          DnsNameResolver dnsNameResolver) {
        this(true, validateNonce, ioTransport, dnsNameResolver);
    }

    /**
     * Create a new {@link IoTransport} instance
     *
     * @param closeAndThrowIfNotValid If set to {@code true} then we will close the channel and throw an exception
     *                                when certificate is not {@link OcspResponse.Status#VALID}.
     *                                If set to {@code false} then we will simply pass the {@link OcspValidationEvent}
     *                                to the next handler in pipeline and let it decide what to do.
     * @param validateNonce           Set to {@code true} if we should force nonce validation on
     *                                OCSP response else set to {@code false}
     * @param ioTransport             {@link IoTransport} to use
     * @param dnsNameResolver         {@link DnsNameResolver} implementation to use
     */
    public OcspServerCertificateValidator(boolean closeAndThrowIfNotValid, boolean validateNonce,
                                          IoTransport ioTransport, DnsNameResolver dnsNameResolver) {
        this.closeAndThrowIfNotValid = closeAndThrowIfNotValid;
        this.validateNonce = validateNonce;
        this.ioTransport = checkNotNull(ioTransport, "IoTransport");
        this.dnsNameResolver = checkNotNull(dnsNameResolver, "DnsNameResolver");
    }

    protected static DnsNameResolver createDefaultResolver(final IoTransport ioTransport) {
        return new DnsNameResolverBuilder()
                .eventLoop(ioTransport.eventLoop())
                .channelFactory(ioTransport.datagramChannel())
                .socketChannelFactory(ioTransport.socketChannel())
                .build();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);

        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent sslHandshakeCompletionEvent = (SslHandshakeCompletionEvent) evt;

            // If TLS handshake was successful then only we will perform OCSP certificate validation.
            // If not, then just forward the event to next handler in pipeline and remove ourselves from pipeline.
            if (sslHandshakeCompletionEvent.isSuccess()) {
                Certificate[] certificates = ctx.pipeline().get(SslHandler.class)
                        .engine()
                        .getSession()
                        .getPeerCertificates();

                assert certificates.length <= 2 : "There must an end-entity certificate and issuer certificate";

                Promise<BasicOCSPResp> ocspRespPromise = OcspClient.query((X509Certificate) certificates[0],
                        (X509Certificate) certificates[1], validateNonce, ioTransport, dnsNameResolver);

                ocspRespPromise.addListener(new GenericFutureListener<Future<BasicOCSPResp>>() {
                    @Override
                    public void operationComplete(Future<BasicOCSPResp> future) throws Exception {
                        // If Future is success then we have successfully received OCSP response
                        // from OCSP responder. We will validate it now and process.
                        if (future.isSuccess()) {
                            SingleResp response = future.get().getResponses()[0];

                            Date current = new Date();
                            if (!(current.after(response.getThisUpdate()) &&
                                    current.before(response.getNextUpdate()))) {
                                ctx.fireExceptionCaught(new IllegalStateException("OCSP Response is out-of-date"));
                            }

                            OcspResponse.Status status;
                            if (response.getCertStatus() == null) {
                                // 'null' means certificate is valid
                                status = OcspResponse.Status.VALID;
                            } else if (response.getCertStatus() instanceof RevokedStatus) {
                                status = OcspResponse.Status.REVOKED;
                            } else {
                                status = OcspResponse.Status.UNKNOWN;
                            }

                            ctx.fireUserEventTriggered(new OcspValidationEvent(
                                    new OcspResponse(status, response.getThisUpdate(), response.getNextUpdate())));

                            // If Certificate is not VALID and 'closeAndThrowIfNotValid' is set
                            // to 'true' then close the channel and throw an exception.
                            if (status != OcspResponse.Status.VALID && closeAndThrowIfNotValid) {
                                ctx.channel().close();
                                // Certificate is not valid. Throw
                                ctx.fireExceptionCaught(new OCSPException(
                                        "Certificate not valid. Status: " + status));
                            }
                        } else {
                            ctx.fireExceptionCaught(future.cause());
                        }
                    }
                });
            }
            // Lets remove ourselves from the pipeline because we are done processing validation.
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.channel().close();
    }
}
