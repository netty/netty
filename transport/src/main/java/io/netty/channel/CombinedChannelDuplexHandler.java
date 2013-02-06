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

import java.net.SocketAddress;

/**
 *  Combines a {@link ChannelStateHandler} and a {@link ChannelOperationHandler} into one {@link ChannelHandler}.
 *
 */
public class CombinedChannelDuplexHandler extends ChannelDuplexHandler {

    protected final ChannelStateHandler stateHandler;
    protected final ChannelOperationHandler operationHandler;

    protected CombinedChannelDuplexHandler() {
        ChannelStateHandler stateHandler = newStateHandler();
        ChannelOperationHandler operationHandler = newOperationHandler();
        validate(stateHandler, operationHandler);
        this.stateHandler = stateHandler;
        this.operationHandler = operationHandler;
    }

    /**
     * Combine the given {@link ChannelStateHandler} and {@link ChannelOperationHandler}.
     */
    public CombinedChannelDuplexHandler(ChannelStateHandler stateHandler, ChannelOperationHandler operationHandler) {
        validate(stateHandler, operationHandler);
        this.stateHandler = stateHandler;
        this.operationHandler = operationHandler;
    }

    /**
     * Creates a new {@link ChannelStateHandler} that this handler delegates events to.  Please note this method
     * is only invoked when this handler was constructed via
     * {@linkplain #CombinedChannelDuplexHandler() the default constructor} rather than
     * {@linkplain #CombinedChannelDuplexHandler(ChannelStateHandler, ChannelOperationHandler) the other one}.
     */
    protected ChannelStateHandler newStateHandler() {
        fail();
        return null;
    }

    /**
     * Creates a new {@link ChannelOperationHandler} that this handler delegates events to.  Please note this method
     * is only invoked when this handler was constructed via
     * {@linkplain #CombinedChannelDuplexHandler() the default constructor} rather than
     * {@linkplain #CombinedChannelDuplexHandler(ChannelStateHandler, ChannelOperationHandler) the other one}.
     */
    protected ChannelOperationHandler newOperationHandler() {
        fail();
        return null;
    }

    private static void fail() {
        throw new IllegalStateException(
                "subclass must implement this method to call the default constructor of " +
                        CombinedChannelDuplexHandler.class.getSimpleName());
    }

    @SuppressWarnings("InstanceofIncompatibleInterface")
    private void validate(ChannelStateHandler stateHandler, ChannelOperationHandler operationHandler) {
        if (stateHandler == null) {
            throw new NullPointerException("stateHandler");
        }
        if (operationHandler == null) {
            throw new NullPointerException("operationHandler");
        }
        if (stateHandler instanceof ChannelOperationHandler) {
            throw new IllegalArgumentException(
                    "stateHandler must not implement " +
                    ChannelOperationHandler.class.getSimpleName() + " to get combined.");
        }
        if (operationHandler instanceof ChannelStateHandler) {
            throw new IllegalArgumentException(
                    "operationHandler must not implement " +
                    ChannelStateHandler.class.getSimpleName() + " to get combined.");
        }

        if (stateHandler instanceof ChannelInboundByteHandler && !(this instanceof ChannelInboundByteHandler)) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " must implement " + ChannelInboundByteHandler.class.getSimpleName() +
                    " if stateHandler implements " + ChannelInboundByteHandler.class.getSimpleName());
        }

        if (stateHandler instanceof ChannelInboundMessageHandler && !(this instanceof ChannelInboundMessageHandler)) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " must implement " +
                    ChannelInboundMessageHandler.class.getSimpleName() + " if stateHandler implements " +
                    ChannelInboundMessageHandler.class.getSimpleName());
        }

        if (operationHandler instanceof ChannelOutboundByteHandler && !(this instanceof ChannelOutboundByteHandler)) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " must implement " +
                    ChannelOutboundByteHandler.class.getSimpleName() + " if operationHandler implements " +
                    ChannelOutboundByteHandler.class.getSimpleName());
        }

        if (operationHandler instanceof ChannelOutboundMessageHandler &&
            !(this instanceof ChannelOutboundMessageHandler)) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " must implement " +
                    ChannelOutboundMessageHandler.class.getSimpleName() + " if operationHandler implements " +
                    ChannelOutboundMessageHandler.class.getSimpleName());
        }
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        try {
            stateHandler.beforeAdd(ctx);
        } finally {
            operationHandler.beforeAdd(ctx);
        }
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        try {
            stateHandler.afterAdd(ctx);
        } finally {
            operationHandler.afterAdd(ctx);
        }
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        try {
            stateHandler.beforeRemove(ctx);
        } finally {
            operationHandler.beforeRemove(ctx);
        }
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        try {
            stateHandler.afterRemove(ctx);
        } finally {
            operationHandler.afterRemove(ctx);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        stateHandler.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        stateHandler.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        stateHandler.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        stateHandler.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        stateHandler.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        stateHandler.userEventTriggered(ctx, evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        stateHandler.inboundBufferUpdated(ctx);
        if (stateHandler instanceof ChannelInboundByteHandler) {
            ((ChannelInboundByteHandler) stateHandler).discardInboundReadBytes(ctx);
        }
    }

    @Override
    public void bind(
            ChannelHandlerContext ctx,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        operationHandler.bind(ctx, localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        operationHandler.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        operationHandler.disconnect(ctx, promise);
    }

    @Override
    public void close(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        operationHandler.close(ctx, promise);
    }

    @Override
    public void deregister(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        operationHandler.deregister(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        operationHandler.read(ctx);
    }

    @Override
    public void flush(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        operationHandler.flush(ctx, promise);
        if (operationHandler instanceof ChannelOutboundByteHandler) {
            ((ChannelOutboundByteHandler) operationHandler).discardOutboundReadBytes(ctx);
        }
    }

    @Override
    public void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
        operationHandler.sendFile(ctx, region, promise);
    }
}
