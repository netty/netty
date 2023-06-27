/*
 * Copyright 2018 The Netty Project
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

import io.netty.internal.tcnative.SSL;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Special {@link SSLEngine} which allows to wrap a {@link ReferenceCountedOpenSslEngine} and verify that that
 * Error stack is empty after each method call.
 */
final class OpenSslErrorStackAssertSSLEngine extends JdkSslEngine implements ReferenceCounted {

    OpenSslErrorStackAssertSSLEngine(ReferenceCountedOpenSslEngine engine) {
        super(engine);
    }

    @Override
    public String getPeerHost() {
        try {
            return getWrappedEngine().getPeerHost();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public int getPeerPort() {
        try {
            return getWrappedEngine().getPeerPort();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        try {
            return getWrappedEngine().wrap(src, dst);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, ByteBuffer dst) throws SSLException {
        try {
            return getWrappedEngine().wrap(srcs, dst);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] byteBuffers, int i, int i1, ByteBuffer byteBuffer) throws SSLException {
        try {
            return getWrappedEngine().wrap(byteBuffers, i, i1, byteBuffer);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        try {
            return getWrappedEngine().unwrap(src, dst);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts) throws SSLException {
        try {
            return getWrappedEngine().unwrap(src, dsts);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers, int i, int i1) throws SSLException {
        try {
            return getWrappedEngine().unwrap(byteBuffer, byteBuffers, i, i1);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public Runnable getDelegatedTask() {
        try {
            return getWrappedEngine().getDelegatedTask();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void closeInbound() throws SSLException {
        try {
            getWrappedEngine().closeInbound();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean isInboundDone() {
        try {
            return getWrappedEngine().isInboundDone();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void closeOutbound() {
        try {
            getWrappedEngine().closeOutbound();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean isOutboundDone() {
        try {
            return getWrappedEngine().isOutboundDone();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public String[] getSupportedCipherSuites() {
        try {
            return getWrappedEngine().getSupportedCipherSuites();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public String[] getEnabledCipherSuites() {
        try {
            return getWrappedEngine().getEnabledCipherSuites();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setEnabledCipherSuites(String[] strings) {
        try {
            getWrappedEngine().setEnabledCipherSuites(strings);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public String[] getSupportedProtocols() {
        try {
            return getWrappedEngine().getSupportedProtocols();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public String[] getEnabledProtocols() {
        try {
            return getWrappedEngine().getEnabledProtocols();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setEnabledProtocols(String[] strings) {
        try {
            getWrappedEngine().setEnabledProtocols(strings);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLSession getSession() {
        try {
            return getWrappedEngine().getSession();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLSession getHandshakeSession() {
        try {
            return getWrappedEngine().getHandshakeSession();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void beginHandshake() throws SSLException {
        try {
            getWrappedEngine().beginHandshake();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        try {
            return getWrappedEngine().getHandshakeStatus();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setUseClientMode(boolean b) {
        try {
            getWrappedEngine().setUseClientMode(b);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean getUseClientMode() {
        try {
            return getWrappedEngine().getUseClientMode();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        try {
            getWrappedEngine().setNeedClientAuth(b);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean getNeedClientAuth() {
        try {
            return getWrappedEngine().getNeedClientAuth();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setWantClientAuth(boolean b) {
        try {
            getWrappedEngine().setWantClientAuth(b);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean getWantClientAuth() {
        try {
            return getWrappedEngine().getWantClientAuth();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        try {
            getWrappedEngine().setEnableSessionCreation(b);
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public boolean getEnableSessionCreation() {
        try {
            return getWrappedEngine().getEnableSessionCreation();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public SSLParameters getSSLParameters() {
        try {
            return getWrappedEngine().getSSLParameters();
        } finally {
            assertErrorStackEmpty();
        }
    }

    @Override
    public void setSSLParameters(SSLParameters params) {
        try {
            getWrappedEngine().setSSLParameters(params);
        } finally {
            assertErrorStackEmpty();
        }
    }

    public String getApplicationProtocol() {
        if (PlatformDependent.javaVersion() >= 9) {
            try {
                return JdkAlpnSslUtils.getApplicationProtocol(getWrappedEngine());
            } finally {
                assertErrorStackEmpty();
            }
        }
        throw new UnsupportedOperationException();
    }

    public String getHandshakeApplicationProtocol() {
        if (PlatformDependent.javaVersion() >= 9) {
            try {
                return JdkAlpnSslUtils.getHandshakeApplicationProtocol(getWrappedEngine());
            } finally {
                assertErrorStackEmpty();
            }
        }
        throw new UnsupportedOperationException();
    }

    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        if (PlatformDependent.javaVersion() >= 9) {
            try {
                JdkAlpnSslUtils.setHandshakeApplicationProtocolSelector(getWrappedEngine(), selector);
            } finally {
                assertErrorStackEmpty();
            }
        }
        throw new UnsupportedOperationException();
    }

    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        if (PlatformDependent.javaVersion() >= 9) {
            try {
                return JdkAlpnSslUtils.getHandshakeApplicationProtocolSelector(getWrappedEngine());
            } finally {
                assertErrorStackEmpty();
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int refCnt() {
        return getWrappedEngine().refCnt();
    }

    @Override
    public OpenSslErrorStackAssertSSLEngine retain() {
        getWrappedEngine().retain();
        return this;
    }

    @Override
    public OpenSslErrorStackAssertSSLEngine retain(int increment) {
        getWrappedEngine().retain(increment);
        return this;
    }

    @Override
    public OpenSslErrorStackAssertSSLEngine touch() {
        getWrappedEngine().touch();
        return this;
    }

    @Override
    public OpenSslErrorStackAssertSSLEngine touch(Object hint) {
        getWrappedEngine().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return getWrappedEngine().release();
    }

    @Override
    public boolean release(int decrement) {
        return getWrappedEngine().release(decrement);
    }

    @Override
    public String getNegotiatedApplicationProtocol() {
        return getWrappedEngine().getNegotiatedApplicationProtocol();
    }

    @Override
    void setNegotiatedApplicationProtocol(String applicationProtocol) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReferenceCountedOpenSslEngine getWrappedEngine() {
        return (ReferenceCountedOpenSslEngine) super.getWrappedEngine();
    }

    private static void assertErrorStackEmpty() {
        long error = SSL.getLastErrorNumber();
        assertEquals(0, error, "SSL error stack non-empty: " + SSL.getErrorString(error));
    }
}
