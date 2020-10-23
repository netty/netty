/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.AsyncMapping;
import io.netty.util.DomainNameMapping;
import io.netty.util.Mapping;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

/**
 * <p>Enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">SNI
 * (Server Name Indication)</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name.</p>
 */
public class SniHandler extends AbstractSniHandler<SslContext> {
    private static final Selection EMPTY_SELECTION = new Selection(null, null);

    protected final AsyncMapping<String, SslContext> mapping;

    private volatile Selection selection = EMPTY_SELECTION;

    /**
     * Creates a SNI detection handler with configured {@link SslContext}
     * maintained by {@link Mapping}
     *
     * @param mapping the mapping of domain name to {@link SslContext}
     */
    public SniHandler(Mapping<? super String, ? extends SslContext> mapping) {
        this(new AsyncMappingAdapter(mapping));
    }

    /**
     * Creates a SNI detection handler with configured {@link SslContext}
     * maintained by {@link DomainNameMapping}
     *
     * @param mapping the mapping of domain name to {@link SslContext}
     */
    public SniHandler(DomainNameMapping<? extends SslContext> mapping) {
        this((Mapping<String, ? extends SslContext>) mapping);
    }

    /**
     * Creates a SNI detection handler with configured {@link SslContext}
     * maintained by {@link AsyncMapping}
     *
     * @param mapping the mapping of domain name to {@link SslContext}
     */
    @SuppressWarnings("unchecked")
    public SniHandler(AsyncMapping<? super String, ? extends SslContext> mapping) {
        this.mapping = (AsyncMapping<String, SslContext>) ObjectUtil.checkNotNull(mapping, "mapping");
    }

    /**
     * @return the selected hostname
     */
    public String hostname() {
        return selection.hostname;
    }

    /**
     * @return the selected {@link SslContext}
     */
    public SslContext sslContext() {
        return selection.context;
    }

    /**
     * The default implementation will simply call {@link AsyncMapping#map(Object, Promise)} but
     * users can override this method to implement custom behavior.
     *
     * @see AsyncMapping#map(Object, Promise)
     */
    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
        return mapping.map(hostname, ctx.executor().<SslContext>newPromise());
    }

    @Override
    protected final void onLookupComplete(ChannelHandlerContext ctx,
                                          String hostname, Future<SslContext> future) throws Exception {
        if (!future.isSuccess()) {
            final Throwable cause = future.cause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new DecoderException("failed to get the SslContext for " + hostname, cause);
        }

        SslContext sslContext = future.getNow();
        selection = new Selection(sslContext, hostname);
        try {
            replaceHandler(ctx, hostname, sslContext);
        } catch (Throwable cause) {
            selection = EMPTY_SELECTION;
            PlatformDependent.throwException(cause);
        }
    }

    /**
     * The default implementation of this method will simply replace {@code this} {@link SniHandler}
     * instance with a {@link SslHandler}. Users may override this method to implement custom behavior.
     *
     * Please be aware that this method may get called after a client has already disconnected and
     * custom implementations must take it into consideration when overriding this method.
     *
     * It's also possible for the hostname argument to be {@code null}.
     */
    protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
        SslHandler sslHandler = null;
        try {
            sslHandler = newSslHandler(sslContext, ctx.alloc());
            ctx.pipeline().replace(this, SslHandler.class.getName(), sslHandler);
            sslHandler = null;
        } finally {
            // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was not
            // transferred to the SslHandler.
            // See https://github.com/netty/netty/issues/5678
            if (sslHandler != null) {
                ReferenceCountUtil.safeRelease(sslHandler.engine());
            }
        }
    }

    /**
     * Returns a new {@link SslHandler} using the given {@link SslContext} and {@link ByteBufAllocator}.
     * Users may override this method to implement custom behavior.
     */
    protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
        return context.newHandler(allocator);
    }

    private static final class AsyncMappingAdapter implements AsyncMapping<String, SslContext> {
        private final Mapping<? super String, ? extends SslContext> mapping;

        private AsyncMappingAdapter(Mapping<? super String, ? extends SslContext> mapping) {
            this.mapping = ObjectUtil.checkNotNull(mapping, "mapping");
        }

        @Override
        public Future<SslContext> map(String input, Promise<SslContext> promise) {
            final SslContext context;
            try {
                context = mapping.map(input);
            } catch (Throwable cause) {
                return promise.setFailure(cause);
            }
            return promise.setSuccess(context);
        }
    }

    private static final class Selection {
        final SslContext context;
        final String hostname;

        Selection(SslContext context, String hostname) {
            this.context = context;
            this.hostname = hostname;
        }
    }
}
