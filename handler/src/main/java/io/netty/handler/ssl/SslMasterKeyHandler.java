/*
 * Copyright 2019 The Netty Project
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

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.ReflectionUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;

/**
 * The {@link SslMasterKeyHandler} is a channel-handler you can include in your pipeline to consume the master key
 * & session identifier for a TLS session.
 * This can be very useful, for instance the {@link WiresharkSslMasterKeyHandler} implementation will
 * log the secret & identifier in a format that is consumable by Wireshark -- allowing easy decryption of pcap/tcpdumps.
 */
public abstract class SslMasterKeyHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslMasterKeyHandler.class);

    /**
     * The JRE SSLSessionImpl cannot be imported
     */
    private static final Class<?> SSL_SESSIONIMPL_CLASS;

    /**
     * The master key field in the SSLSessionImpl
     */
    private static final Field SSL_SESSIONIMPL_MASTER_SECRET_FIELD;

    /**
     * A system property that can be used to turn on/off the {@link SslMasterKeyHandler} dynamically without having
     * to edit your pipeline.
     * <code>-Dio.netty.ssl.masterKeyHandler=true</code>
     */
    public static final String SYSTEM_PROP_KEY = "io.netty.ssl.masterKeyHandler";

    /**
     * The unavailability cause of whether the private Sun implementation of SSLSessionImpl is available.
     */
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause;
        Class<?> clazz = null;
        Field field = null;
        try {
            clazz = Class.forName("sun.security.ssl.SSLSessionImpl");
            field = clazz.getDeclaredField("masterSecret");
            cause = ReflectionUtil.trySetAccessible(field, true);
        } catch (Throwable e) {
            cause = e;
            if (logger.isTraceEnabled()) {
                logger.debug("sun.security.ssl.SSLSessionImpl is unavailable.", e);
            } else {
                logger.debug("sun.security.ssl.SSLSessionImpl is unavailable: {}", e.getMessage());
            }
        }
        UNAVAILABILITY_CAUSE = cause;
        SSL_SESSIONIMPL_CLASS = clazz;
        SSL_SESSIONIMPL_MASTER_SECRET_FIELD = field;
    }

    /**
     * Constructor.
    */
    protected SslMasterKeyHandler() {
    }

    /**
     * Ensure that SSLSessionImpl is available.
     * @throws UnsatisfiedLinkError if unavailable
     */
    public static void ensureSunSslEngineAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw new IllegalStateException(
                    "Failed to find SSLSessionImpl on classpath", UNAVAILABILITY_CAUSE);
        }
    }

    /**
     * Returns the cause of unavailability.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable sunSslEngineUnavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    /* Returns {@code true} if and only if sun.security.ssl.SSLSessionImpl exists in the runtime.
     */
    public static boolean isSunSslEngineAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Consume the master key for the session and the sessionId
     * @param masterKey A 48-byte secret shared between the client and server.
     * @param session The current TLS session
     */
    protected abstract void accept(SecretKey masterKey, SSLSession session);

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        //only try to log the session info if the ssl handshake has successfully completed.
        if (evt == SslHandshakeCompletionEvent.SUCCESS && masterKeyHandlerEnabled()) {
            final SslHandler handler = ctx.pipeline().get(SslHandler.class);
            final SSLEngine engine = handler.engine();
            final SSLSession sslSession = engine.getSession();

            //the OpenJDK does not expose a way to get the master secret, so try to use reflection to get it.
            if (isSunSslEngineAvailable() && sslSession.getClass().equals(SSL_SESSIONIMPL_CLASS)) {
                final SecretKey secretKey;
                try {
                    secretKey = (SecretKey) SSL_SESSIONIMPL_MASTER_SECRET_FIELD.get(sslSession);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("Failed to access the field 'masterSecret' " +
                            "via reflection.", e);
                }
                accept(secretKey, sslSession);
            } else if (OpenSsl.isAvailable() && engine instanceof ReferenceCountedOpenSslEngine) {
                SecretKeySpec secretKey = ((ReferenceCountedOpenSslEngine) engine).masterKey();
                accept(secretKey, sslSession);
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    /**
     * Checks if the handler is set up to actually handle/accept the event.
     * By default the {@link #SYSTEM_PROP_KEY} property is checked, but any implementations of this class are
     * free to override if they have different mechanisms of checking.
     *
     * @return true if it should handle, false otherwise.
     */
    protected boolean masterKeyHandlerEnabled() {
        return SystemPropertyUtil.getBoolean(SYSTEM_PROP_KEY, false);
    }

    /**
     * Create a {@link WiresharkSslMasterKeyHandler} instance.
     * This TLS master key handler logs the master key and session-id in a format
     * understood by Wireshark -- this can be especially useful if you need to ever
     * decrypt a TLS session and are using perfect forward secrecy (i.e. Diffie-Hellman)
     * The key and session identifier are forwarded to the log named 'io.netty.wireshark'.
     */
    public static SslMasterKeyHandler newWireSharkSslMasterKeyHandler() {
        return new WiresharkSslMasterKeyHandler();
    }

    /**
     * Record the session identifier and master key to the {@link InternalLogger} named {@code io.netty.wireshark}.
     * ex. {@code RSA Session-ID:XXX Master-Key:YYY}
     * This format is understood by Wireshark 1.6.0.
     * See: <a href=
     * "https://code.wireshark.org/review/gitweb?p=wireshark.git;a=commit;h=686d4cabb41185591c361f9ec6b709034317144b"
     * >Wireshark</a>
     * The key and session identifier are forwarded to the log named 'io.netty.wireshark'.
     */
    private static final class WiresharkSslMasterKeyHandler extends SslMasterKeyHandler {

        private static final InternalLogger wireshark_logger =
                InternalLoggerFactory.getInstance("io.netty.wireshark");

        @Override
        protected void accept(SecretKey masterKey, SSLSession session) {
            if (masterKey.getEncoded().length != 48) {
                throw new IllegalArgumentException("An invalid length master key was provided.");
            }
            final byte[] sessionId = session.getId();
            wireshark_logger.warn("RSA Session-ID:{} Master-Key:{}",
                    ByteBufUtil.hexDump(sessionId).toLowerCase(),
                    ByteBufUtil.hexDump(masterKey.getEncoded()).toLowerCase());
        }
    }

}
