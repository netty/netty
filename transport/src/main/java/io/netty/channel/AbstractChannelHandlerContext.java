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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    private final boolean inbound;
    private final boolean outbound;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private boolean handlerRemoved;

    /**
     * This is set to {@code true} once the {@link ChannelHandler#handlerAdded(ChannelHandlerContext) method was called.
     * We need to keep track of this This will set to true once the
     * {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} method was called. We need to keep track of this
     * to ensure we will never call another {@link ChannelHandler} method before handlerAdded(...) was called
     * to guard againstordering issues. {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} MUST be the first
     * method that is called for handler when it becomes a part of the {@link ChannelPipeline} in all cases. Not doing
     * so may lead to unexpected side-effects as {@link ChannelHandler} implementationsmay need to do initialization
     * steps before a  {@link ChannelHandler} can be used.
     *
     * See <a href="https://github.com/netty/netty/issues/4705">#4705</a>
     *
     * No need to mark volatile as this will be made visible as next/prev is volatile.
     */
    private boolean handlerAdded;

    final ChannelHandlerInvoker invoker;
    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // These needs to be volatile as otherwise an other Thread may see an half initialized instance.
    // See the JMM for more details
    volatile Runnable invokeChannelReadCompleteTask;
    volatile Runnable invokeReadTask;
    volatile Runnable invokeChannelWritableStateChangedTask;
    volatile Runnable invokeFlushTask;

    AbstractChannelHandlerContext(
            DefaultChannelPipeline pipeline, ChannelHandlerInvoker invoker,
            String name, boolean inbound, boolean outbound) {

        if (name == null) {
            throw new NullPointerException("name");
        }

        this.pipeline = pipeline;
        this.name = name;
        this.invoker = invoker;

        this.inbound = inbound;
        this.outbound = outbound;
    }

    @Override
    public Channel channel() {
        return pipeline.channel();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public EventExecutor executor() {
        return invoker().executor();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelRegistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelUnregistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelActive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelInactive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext next = this.next;
        next.invokeExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeUserEventTriggered(event);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelRead(pipeline.touch(msg, next));
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelReadComplete();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelWritabilityChanged();
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeBind(localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeConnect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        if (!channel().metadata().hasDisconnect()) {
            return close(promise);
        }

        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeDisconnect(promise);
        return promise;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeClose(promise);
        return promise;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeDeregister(promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext read() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeRead();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeWrite(pipeline.touch(msg, next), promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext flush() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeFlush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeWriteAndFlush(pipeline.touch(msg, next), promise);
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel(), executor());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        ChannelFuture succeededFuture = this.succeededFuture;
        if (succeededFuture == null) {
            this.succeededFuture = succeededFuture = new SucceededChannelFuture(channel(), executor());
        }
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel(), executor(), cause);
    }

    private AbstractChannelHandlerContext findContextInbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!ctx.inbound);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!ctx.outbound);
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel().voidPromise();
    }

    void setRemoved() {
        handlerRemoved = true;
    }

    @Override
    public boolean isRemoved() {
        return handlerRemoved;
    }

    final void invokeChannelRegistered() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelRegistered(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelRegistered(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeChannelUnregistered() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelUnregistered(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelUnregistered(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeChannelActive() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelActive(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelActive(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeChannelInactive() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelInactive(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelInactive(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeExceptionCaught(final Throwable cause) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeExceptionCaught(this, cause);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeExceptionCaught(AbstractChannelHandlerContext.this, cause);
                }
            });
        }
    }

    final void invokeUserEventTriggered(final Object event) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeUserEventTriggered(this, event);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeUserEventTriggered(AbstractChannelHandlerContext.this, event);
                }
            });
        }
    }

    final void invokeChannelRead(final Object msg) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelRead(this, msg);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelRead(AbstractChannelHandlerContext.this, msg);
                }
            });
        }
    }

    final void invokeChannelReadComplete() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelReadComplete(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelReadComplete(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeChannelWritabilityChanged() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeChannelWritabilityChanged(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeChannelWritabilityChanged(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeBind(final SocketAddress localAddress, final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeBind(this, localAddress, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeBind(AbstractChannelHandlerContext.this, localAddress, promise);
                }
            });
        }
    }

    final void invokeConnect(final SocketAddress remoteAddress,
                              final SocketAddress localAddress, final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeConnect(this, remoteAddress, localAddress, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeConnect(AbstractChannelHandlerContext.this, remoteAddress, localAddress, promise);
                }
            });
        }
    }

    final void invokeDisconnect(final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeDisconnect(this, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeDisconnect(AbstractChannelHandlerContext.this, promise);
                }
            });
        }
    }

    final void invokeClose(final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeClose(this, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeClose(AbstractChannelHandlerContext.this, promise);
                }
            });
        }
    }

    final void invokeDeregister(final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeDeregister(this, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeDeregister(AbstractChannelHandlerContext.this, promise);
                }
            });
        }
    }

    final void invokeRead() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeRead(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeRead(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeWrite(final Object msg, final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeWrite(this, msg, promise);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeWrite(AbstractChannelHandlerContext.this, msg, promise);
                }
            });
        }
    }

    final void invokeFlush() {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeFlush(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeFlush(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    final void invokeWriteAndFlush(final Object msg, final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        if (handlerAdded) {
            invoker.invokeWrite(this, msg, promise);
            invoker.invokeFlush(this);
        } else {
            invoker.executor().execute(new OneTimeTask() {
                @Override
                public void run() {
                    assert handlerAdded;
                    invoker.invokeWrite(AbstractChannelHandlerContext.this, msg, promise);
                    invoker.invokeFlush(AbstractChannelHandlerContext.this);
                }
            });
        }
    }

    @Override
    public ChannelHandlerInvoker invoker() {
        return invoker == null ? channel().unsafe().invoker() : invoker;
    }

    final void setHandlerAddedCalled() {
        handlerAdded = true;
    }

    @Override
    public String toHintString() {
        return '\'' + name + "' will handle the message from this point.";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ChannelHandlerContext.class) + '(' + name + ", " + channel() + ')';
    }
}
