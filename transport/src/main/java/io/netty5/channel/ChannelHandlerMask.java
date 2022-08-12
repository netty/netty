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
package io.netty5.channel;

import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.WeakHashMap;

final class ChannelHandlerMask {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelHandlerMask.class);

    // Using to mask which methods must be called for a ChannelHandler.
    static final int MASK_CHANNEL_EXCEPTION_CAUGHT = 1;
    static final int MASK_CHANNEL_REGISTERED = 1 << 1;
    static final int MASK_CHANNEL_UNREGISTERED = 1 << 2;
    static final int MASK_CHANNEL_ACTIVE = 1 << 3;
    static final int MASK_CHANNEL_INACTIVE = 1 << 4;
    static final int MASK_CHANNEL_SHUTDOWN = 1 << 5;
    static final int MASK_CHANNEL_READ = 1 << 6;
    static final int MASK_CHANNEL_READ_COMPLETE = 1 << 7;
    static final int MASK_CHANNEL_INBOUND_EVENT = 1 << 8;
    static final int MASK_CHANNEL_WRITABILITY_CHANGED = 1 << 9;
    static final int MASK_BIND = 1 << 10;
    static final int MASK_CONNECT = 1 << 11;
    static final int MASK_DISCONNECT = 1 << 12;
    static final int MASK_CLOSE = 1 << 13;
    static final int MASK_SHUTDOWN = 1 << 14;
    static final int MASK_REGISTER = 1 << 15;
    static final int MASK_DEREGISTER = 1 << 16;
    static final int MASK_READ = 1 << 17;
    static final int MASK_WRITE = 1 << 18;
    static final int MASK_FLUSH = 1 << 19;
    static final int MASK_SEND_OUTBOUND_EVENT = 1 << 20;

    static final int MASK_PENDING_OUTBOUND_BYTES = 1 << 21;

    private static final int MASK_ALL_INBOUND = MASK_CHANNEL_EXCEPTION_CAUGHT | MASK_CHANNEL_REGISTERED |
            MASK_CHANNEL_UNREGISTERED | MASK_CHANNEL_ACTIVE | MASK_CHANNEL_INACTIVE | MASK_CHANNEL_SHUTDOWN |
            MASK_CHANNEL_READ | MASK_CHANNEL_READ_COMPLETE  | MASK_CHANNEL_WRITABILITY_CHANGED |
            MASK_CHANNEL_INBOUND_EVENT;
    private static final int MASK_ALL_OUTBOUND = MASK_BIND | MASK_CONNECT | MASK_DISCONNECT |
            MASK_CLOSE | MASK_SHUTDOWN | MASK_REGISTER | MASK_DEREGISTER | MASK_READ | MASK_WRITE | MASK_FLUSH |
            MASK_SEND_OUTBOUND_EVENT | MASK_PENDING_OUTBOUND_BYTES;

    private static final FastThreadLocal<Map<Class<? extends ChannelHandler>, Integer>> MASKS =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<? extends ChannelHandler>, Integer> initialValue() {
                    return new WeakHashMap<>(32);
                }
            };

    /**
     * Return the {@code executionMask}.
     */
    static int mask(Class<? extends ChannelHandler> clazz) {
        // Try to obtain the mask from the cache first. If this fails calculate it and put it in the cache for fast
        // lookup in the future.
        Map<Class<? extends ChannelHandler>, Integer> cache = MASKS.get();
        Integer mask = cache.get(clazz);
        if (mask == null) {
            mask = mask0(clazz);
            cache.put(clazz, mask);
        }
        return mask;
    }

    static boolean isInbound(Class<? extends ChannelHandler> clazz) {
        return (mask(clazz) & MASK_ALL_INBOUND) != 0;
    }

    static boolean isOutbound(Class<? extends ChannelHandler> clazz) {
        return (mask(clazz) & MASK_ALL_OUTBOUND) != 0;
    }

    /**
     * Calculate the {@code executionMask}.
     */
    private static int mask0(Class<? extends ChannelHandler> handlerType) {
        int mask = 0;
        mask |= MASK_ALL_INBOUND;
        mask |= MASK_ALL_OUTBOUND;

        try {
            if (isSkippable(handlerType, "channelExceptionCaught", ChannelHandlerContext.class, Throwable.class)) {
                mask &= ~MASK_CHANNEL_EXCEPTION_CAUGHT;
            }

            if (isSkippable(handlerType, "channelRegistered", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_REGISTERED;
            }
            if (isSkippable(handlerType, "channelUnregistered", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_UNREGISTERED;
            }
            if (isSkippable(handlerType, "channelActive", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_ACTIVE;
            }
            if (isSkippable(handlerType, "channelInactive", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_INACTIVE;
            }
            if (isSkippable(handlerType, "channelShutdown", ChannelHandlerContext.class,
                    ChannelShutdownDirection.class)) {
                mask &= ~MASK_CHANNEL_SHUTDOWN;
            }
            if (isSkippable(handlerType, "channelRead", ChannelHandlerContext.class, Object.class)) {
                mask &= ~MASK_CHANNEL_READ;
            }
            if (isSkippable(handlerType, "channelReadComplete", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_READ_COMPLETE;
            }
            if (isSkippable(handlerType, "channelWritabilityChanged", ChannelHandlerContext.class)) {
                mask &= ~MASK_CHANNEL_WRITABILITY_CHANGED;
            }
            if (isSkippable(handlerType, "channelInboundEvent", ChannelHandlerContext.class, Object.class)) {
                mask &= ~MASK_CHANNEL_INBOUND_EVENT;
            }
            if (isSkippable(handlerType, "bind", ChannelHandlerContext.class, SocketAddress.class)) {
                mask &= ~MASK_BIND;
            }
            if (isSkippable(handlerType, "connect", ChannelHandlerContext.class, SocketAddress.class,
                    SocketAddress.class)) {
                mask &= ~MASK_CONNECT;
            }
            if (isSkippable(handlerType, "disconnect", ChannelHandlerContext.class)) {
                mask &= ~MASK_DISCONNECT;
            }
            if (isSkippable(handlerType, "close", ChannelHandlerContext.class)) {
                mask &= ~MASK_CLOSE;
            }
            if (isSkippable(handlerType, "shutdown", ChannelHandlerContext.class,
                    ChannelShutdownDirection.class)) {
                mask &= ~MASK_SHUTDOWN;
            }
            if (isSkippable(handlerType, "register", ChannelHandlerContext.class)) {
                mask &= ~MASK_REGISTER;
            }
            if (isSkippable(handlerType, "deregister", ChannelHandlerContext.class)) {
                mask &= ~MASK_DEREGISTER;
            }
            if (isSkippable(handlerType, "read", ChannelHandlerContext.class, ReadBufferAllocator.class)) {
                mask &= ~MASK_READ;
            }
            if (isSkippable(handlerType, "write", ChannelHandlerContext.class, Object.class)) {
                mask &= ~MASK_WRITE;
            }
            if (isSkippable(handlerType, "flush", ChannelHandlerContext.class)) {
                mask &= ~MASK_FLUSH;
            }
            if (isSkippable(handlerType, "sendOutboundEvent", ChannelHandlerContext.class, Object.class)) {
                mask &= ~MASK_SEND_OUTBOUND_EVENT;
            }
            if (isSkippable(handlerType, "pendingOutboundBytes", ChannelHandlerContext.class)) {
                mask &= ~MASK_PENDING_OUTBOUND_BYTES;
            }
        } catch (Exception e) {
            // Should never reach here.
            PlatformDependent.throwException(e);
        }

        return mask;
    }

    private static boolean isSkippable(
            final Class<?> handlerType, final String methodName, final Class<?>... paramTypes) throws Exception {
        return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
            Method m;
            try {
                m = handlerType.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Class {} missing method {}, assume we can not skip execution", handlerType, methodName, e);
                }
                return false;
            }
            return m.isAnnotationPresent(Skip.class);
        });
    }

    private ChannelHandlerMask() { }

    /**
     * Indicates that the annotated event handler method in {@link ChannelHandler} will not be invoked by
     * {@link ChannelPipeline} and so <strong>MUST</strong> only be used when the {@link ChannelHandler}
     * method does nothing except forward to the next {@link ChannelHandler} in the pipeline.
     * <p>
     * Note that this annotation is not {@linkplain Inherited inherited}. If a user overrides a method annotated with
     * {@link Skip}, it will not be skipped anymore. Similarly, the user can override a method not annotated with
     * {@link Skip} and simply pass the event through to the next handler, which reverses the behavior of the
     * supertype.
     * </p>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Skip {
        // no value
    }
}
