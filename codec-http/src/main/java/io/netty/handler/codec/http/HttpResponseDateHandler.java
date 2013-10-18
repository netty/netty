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
package io.netty.handler.codec.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This {@link ChannelOutboundHandlerAdapter} will add the Date: header to the {@link HttpResponse}.
 *
 */
public class HttpResponseDateHandler extends ChannelOutboundHandlerAdapter {
    private String cachedDateString;
    private long nextUpdateTime;
    private final long cachedTimeInMillis;

    /**
     * Create a new instance which caches the time for {@code 1} second.
     */
    public HttpResponseDateHandler() {
        this(1000);
    }

    /**
     * Create a new instance using the given time for how long the same Date header value will be used and so cached.
     * This is done for performance reasons.
     *
     * @param cachedTimeInMillis time in milliseconds
     */
    public HttpResponseDateHandler(long cachedTimeInMillis) {
        this.cachedTimeInMillis = cachedTimeInMillis;
    }

    /**
     * Create a new instance using the given time for how long the same Date header value will be used and so cached.
     * This is done for performance reasons.
     *
     * @param cacheTime time to cache
     * @param unit      the unit in which the time is
     */
    public HttpResponseDateHandler(long cacheTime, TimeUnit unit) {
        this(unit.toMillis(cacheTime));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse) {
            long time = System.currentTimeMillis();
            if (time < nextUpdateTime) {
                ((HttpResponse) msg).headers().set(HttpHeaders.Names.DATE , cachedDateString);
            } else {
                String dateString = HttpHeaderDateFormat.get().format(new Date(time));
                cachedDateString = dateString;
                nextUpdateTime = time + cachedTimeInMillis;
                ((HttpResponse) msg).headers().set(HttpHeaders.Names.DATE, dateString);
            }
        }
        ctx.write(msg, promise);
    }
}
