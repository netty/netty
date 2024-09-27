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
package io.netty.handler.ssl;

import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * An interface that {@code TrustManager} instances can implement, to be notified of resumed SSL sessions.
 * <p>
 * A {@link TrustManager} is called during the TLS handshake, and make decisions about whether
 * the connected peer can be trusted or not. TLS include a feature where previously established sessions can
 * be resumed without going through the trust verification steps.
 * <p>
 * When an {@link SSLSession} is resumed, any values added to it in the prior session may be lost.
 * This interface gives {@link TrustManager} implementations an opportunity to restore any
 * values they would normally add during the TLS handshake, before the handshake completion is signalled
 * to the application.
 * <p>
 * When a session is resumed, the {@link SslHandler} will call the relevant {@code resume*} method,
 * before completing the handshake promise and sending the {@link SslHandshakeCompletionEvent#SUCCESS}
 * event down the pipeline.
 * <p>
 * A trust manager that does not add values to the handshake session in its {@code check*} methods,
 * will typically not have any need to implement this interface.
 * <p>
 * <strong>Note:</strong> The implementing trust manager class must extend {@code X509ExtendedTrustManager},
 * otherwise this interface will be ignored by the {@link SslHandler}.
 */
public interface ResumableX509ExtendedTrustManager extends X509TrustManager {
    /**
     * Given the partial or complete certificate chain recovered from the session ticket,
     * and the {@link SSLEngine} being used, restore the application state of the associated
     * SSL session.
     * <p>
     * This method should obtain the {@link SSLSession} from the {@link SSLEngine#getSession()}
     * method.
     * <p>
     * <strong>Note:</strong> If this method throws {@link CertificateException}, the TLS handshake will not
     * necessarily be rejected. The TLS handshake "Finished" message may have already been sent to the peer
     * by the time this method is called.
     * <p>
     * Implementors should be aware, that peers may make multiple connection attempts using the same session
     * ticket. So this method may be called more than once for the same client, even if prior calls have thrown
     * exceptions or invalidated their sessions.
     * <p>
     * The given certificate chain is not guaranteed to be the authenticated chain. Implementations that need the
     * authenticated certificate chain will have to re-authenticate the certificates. It is recommended to do so
     * with a {@link PKIXBuilderParameters#setDate(Date)} set to the session creation date from
     * {@link SSLSession#getCreationTime()}. Otherwise, the authentication may fail due to the certificate expiring
     * before the session ticket.
     * <p>
     * This method is called on the server-side, restoring sessions for clients.
     *
     * @param chain The peer certificate chain.
     * @param engine The begine used for this connection.
     * @throws CertificateException If the session cannot be restored. Locally, the handshake will appear to have
     * failed, but the peer may have observed a finished handshake.
     */
    void resumeClientTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException;

    /**
     * Given the partial or complete certificate chain recovered of the peer, and the {@link SSLEngine}
     * being used, restore the application state of the associated SSL session.
     * <p>
     * This method should obtain the {@link SSLSession} from the {@link SSLEngine#getSession()}
     * method.
     * <p>
     * <strong>Note:</strong> If this method throws {@link CertificateException}, the TLS handshake will not
     * necessarily be rejected. The TLS handshake "Finished" message may have already been sent to the peer
     * by the time this method is called.
     * <p>
     * Implementors should be aware, that peers may make multiple connection attempts using the same session
     * ticket. So this method may be called more than once for the same client, even if prior calls have thrown
     * exceptions or invalidated their sessions.
     * <p>
     * The given certificate chain is not guaranteed to be the authenticated chain. Implementations that need the
     * authenticated certificate chain will have to re-authenticate the certificates. It is recommended to do so
     * with a {@link PKIXBuilderParameters#setDate(Date)} set to the session creation date from
     * {@link SSLSession#getCreationTime()}. Otherwise, the authentication may fail due to the certificate expiring
     * before the session ticket.
     * <p>
     * This method is called on the client-side, restoring sessions for servers.
     *
     * @param chain The peer certificate chain.
     * @param engine The begine used for this connection.
     * @throws CertificateException If the session cannot be restored. Locally, the handshake will appear to have
     * failed, but the peer may have observed a finished handshake.
     */
    void resumeServerTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException;
}
