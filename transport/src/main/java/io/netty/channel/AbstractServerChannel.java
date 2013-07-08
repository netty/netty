/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.ReferenceCountUtil;

import java.net.SocketAddress;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link #connect(SocketAddress, ChannelPromise)}</li>
 * <li>{@link #disconnect(ChannelPromise)}</li>
 * <li>{@link #write(Object)}</li>
 * <li>{@link #flush(ChannelPromise)}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel() {
        super(null);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultServerUnsafe();
    }

    @Override
    protected int doWrite(Object[] msgs, int msgsLength, int startIndex) throws Exception {
        throw new UnsupportedOperationException();
    }

    private final class DefaultServerUnsafe extends AbstractUnsafe {
        @Override
        public void write(Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void flush(ChannelPromise promise) {
            reject(promise);
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            reject(promise);
        }

        private void reject(ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}
