/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.execution.seda;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ChildChannelStateEvent;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.WriteCompletionEvent;
import io.netty.handler.execution.ChannelEventRunnable;

/**
 * {@link SimpleSedaExecutor} which offers an easy way to handle {@link ChannelEvent}'s in a more fine grained fashion. Sub-classes of this {@link FineGrainedSedaExecutor} should override needed methods to hand over the event to a specific
 * {@link Executor}. By default all events will get passed to the {@link Executor}'s which were given to construct the {@link FineGrainedSedaExecutor}.
 * 
 * This class is marked abstract to make it clear that it should only be used for sub-classing. If you only need to pass upstream/downstream events to a different {@link Executor} use {@link SimpleSedaExecutor}.
 * 
 *
 */
public abstract class FineGrainedSedaExecutor extends SimpleSedaExecutor{

    public FineGrainedSedaExecutor(Executor upstreamExecutor, Executor downstreamExecutor) {
        super(upstreamExecutor, downstreamExecutor);
    }

    public FineGrainedSedaExecutor(Executor executor) {
        super(executor);
    }

    @Override
    protected final void executeDownstream(ChannelDownstreamEventRunnable runnable) throws Exception {
        ChannelEvent e = runnable.getEvent();

        if (e instanceof MessageEvent) {
            executeWriteRequested(runnable, (MessageEvent) e);
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (!Boolean.TRUE.equals(evt.getValue())) {
                    executeCloseRequested(runnable, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    executeBindRequested(runnable, evt);
                } else {
                    executeUnbindRequested(runnable, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    executeConnectRequested(runnable, evt);
                } else {
                    executeDisconnectRequested(runnable, evt);
                }
                break;
            case INTEREST_OPS:
                executeSetInterestOpsRequested(runnable, evt);
                break;
            default:
                super.executeDownstream(runnable);
                break;
            }
        } else {
            super.executeDownstream(runnable);
        }
    }

    /**
     * Invoked when {@link Channel#write(Object)} is called.
     */
    public void executeWriteRequested(ChannelDownstreamEventRunnable runnable, MessageEvent e) throws Exception {
        super.executeDownstream(runnable);
    }

    /**
     * Invoked when {@link Channel#bind(SocketAddress)} was called.
     */
    public void executeBindRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);

    }

    /**
     * Invoked when {@link Channel#connect(SocketAddress)} was called.
     */
    public void executeConnectRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);

    }

    /**
     * Invoked when {@link Channel#setInterestOps(int)} was called.
     */
    public void executeSetInterestOpsRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);
    }

    /**
     * Invoked when {@link Channel#disconnect()} was called.
     */
    public void executeDisconnectRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);

    }

    /**
     * Invoked when {@link Channel#unbind()} was called.
     */
    public void executeUnbindRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);

    }

    /**
     * Invoked when {@link Channel#close()} was called.
     */
    public void executeCloseRequested(ChannelDownstreamEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeDownstream(runnable);
    }
    
    
    @Override
    protected final void executeUpstream(ChannelEventRunnable runnable) throws Exception {
        ChannelEvent e = runnable.getEvent();
        if (e instanceof MessageEvent) {
            executeMessageReceived(runnable, (MessageEvent) e);
        } else if (e instanceof WriteCompletionEvent) {
            WriteCompletionEvent evt = (WriteCompletionEvent) e;
            executeWriteComplete(runnable, evt);
        } else if (e instanceof ChildChannelStateEvent) {
            ChildChannelStateEvent evt = (ChildChannelStateEvent) e;
            if (evt.getChildChannel().isOpen()) {
                executeChildChannelOpen(runnable, evt);
            } else {
                executeChildChannelClosed(runnable, evt);
            }
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (Boolean.TRUE.equals(evt.getValue())) {
                    executeChannelOpen(runnable, evt);
                } else {
                    executeChannelClosed(runnable, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    executeChannelBound(runnable, evt);
                } else {
                    executeChannelUnbound(runnable, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    executeChannelConnected(runnable, evt);
                } else {
                    executeChannelDisconnected(runnable, evt);
                }
                break;
            case INTEREST_OPS:
                executeChannelInterestChanged(runnable, evt);
                break;
            default:
                super.executeUpstream(runnable);
            }
        } else if (e instanceof ExceptionEvent) {
            executeExceptionCaught(runnable, (ExceptionEvent) e);
        } else {
            super.executeUpstream(runnable);
        }
        
    }

    /**
     * Invoked when a message object (e.g: {@link ChannelBuffer}) was received
     * from a remote peer.
     */
    public void executeMessageReceived(ChannelEventRunnable runnable, MessageEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when an exception was raised by an I/O thread or a
     * {@link ChannelHandler}.
     */
    public void executeExceptionCaught(ChannelEventRunnable runnable, ExceptionEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} is open, but not bound nor connected.
     */
    public void executeChannelOpen(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} is open and bound to a local address,
     * but not connected.
     */
    public void executeChannelBound(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} is open, bound to a local address, and
     * connected to a remote address.
     */
    public void executeChannelConnected(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel}'s {@link Channel#getInterestOps() interestOps}
     * was changed.
     */
    public void executeChannelInterestChanged(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} was disconnected from its remote peer.
     */
    public void executeChannelDisconnected(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} was unbound from the current local address.
     */
    public void executeChannelUnbound(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a {@link Channel} was closed and all its related resources
     * were released.
     */
    public void executeChannelClosed(ChannelEventRunnable runnable, ChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when something was written into a {@link Channel}.
     */
    public void executeWriteComplete(ChannelEventRunnable runnable, WriteCompletionEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a child {@link Channel} was open.
     * (e.g. a server channel accepted a connection)
     */
    public void executeChildChannelOpen(ChannelEventRunnable runnable, ChildChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

    /**
     * Invoked when a child {@link Channel} was closed.
     * (e.g. the accepted connection was closed)
     */
    public void executeChildChannelClosed(ChannelEventRunnable runnable, ChildChannelStateEvent e) throws Exception {
        super.executeUpstream(runnable);
    }

}
