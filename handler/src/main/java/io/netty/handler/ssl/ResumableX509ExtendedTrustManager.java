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
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509TrustManager;

/**
 * An interface that {@code TrustManager} instances can implement, to be notified of resumed SSL sessions.
 * <p>
 * A {@link javax.net.ssl.TrustManager} is called during the TLS handshake, and make decisions about whether
 * the connected peer can be trusted or not. TLS include a feature where previously established sessions can
 * be resumed without going through the trust verification steps.
 * <p>
 * However, some trust manager implementations also extract identity information and store it in the
 * {@link javax.net.ssl.SSLSession} for use by the application. When a session is resumed,
 * the {@code TrustManager} is not called, and the session map is empty, leading to confusion and errors in
 * applications that don't expected this behavior.
 * <p>
 * The trust manager implementation can fix this by implementing this interface. When a session is resumed,
 * the {@link SslHandler} will call the relevant {@code resume*} method, before completing the handshake
 * promise and sending the {@link SslHandshakeCompletionEvent#SUCCESS} event down the pipeline.
 * <p>
 * A trust manager that does not add values to the handshake session in its {@code check*} methods,
 * will typically not have any need to implement this interface.
 * <p>
 * The implementing trust manager class must extend {@code X509ExtendedTrustManager}, otherwise this interface
 * will be ignored by the {@link SslHandler}.
 */
public interface ResumableX509ExtendedTrustManager extends X509TrustManager {
    /**
     * Given the partial or complete certificate chain recovered from the session ticket,
     * and the {@link SSLEngine} being used, restore the application state of the associated
     * SSL session.
     * <p>
     * This method should obtain the {@link javax.net.ssl.SSLSession} from the {@link SSLEngine#getSession()}
     * method.
     *
     * @param chain The peer certificate chain.
     * @param engine The begine used for this connection.
     * @throws CertificateException If the certificate chain is no longer trusted.
     */
    void resumeClientTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException;

    /**
     * Given the partial or complete certificate chain recovered of the peer, and the {@link SSLEngine}
     * being used, restore the application state of the associated SSL session.
     * <p>
     * This method should obtain the {@link javax.net.ssl.SSLSession} from the {@link SSLEngine#getSession()}
     * method.
     *
     * @param chain The peer certificate chain.
     * @param engine The begine used for this connection.
     * @throws CertificateException If the certificate chain is no longer trusted.
     */
    void resumeServerTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException;
}
