/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;

/**
 * A {@link Http2StreamRemovalPolicy} that periodically runs garbage collection on streams that have
 * been marked for removal.
 */
public class DefaultHttp2StreamRemovalPolicy extends ChannelHandlerAdapter implements
        Http2StreamRemovalPolicy, Runnable {

    /**
     * The interval (in ns) at which the removed priority garbage collector runs.
     */
    private static final long GARBAGE_COLLECTION_INTERVAL = SECONDS.toNanos(5);

    private final Queue<Garbage> garbage = new ArrayDeque<Garbage>();
    private ScheduledFuture<?> timerFuture;
    private Action action;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // Schedule the periodic timer for performing the policy check.
        timerFuture = ctx.channel().eventLoop().scheduleWithFixedDelay(this,
                GARBAGE_COLLECTION_INTERVAL,
                GARBAGE_COLLECTION_INTERVAL,
                NANOSECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // Cancel the periodic timer.
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    @Override
    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public void markForRemoval(Http2Stream stream) {
        garbage.add(new Garbage(stream));
    }

    /**
     * Runs garbage collection of any streams marked for removal >
     * {@link #GARBAGE_COLLECTION_INTERVAL} nanoseconds ago.
     */
    @Override
    public void run() {
        if (garbage.isEmpty() || action == null) {
            return;
        }

        long time = System.nanoTime();
        for (;;) {
            Garbage next = garbage.peek();
            if (next == null) {
                break;
            }
            if (time - next.removalTime > GARBAGE_COLLECTION_INTERVAL) {
                garbage.remove();
                action.removeStream(next.stream);
            } else {
                break;
            }
        }
    }

    /**
     * Wrapper around a stream and its removal time.
     */
    private static final class Garbage {
        private final long removalTime = System.nanoTime();
        private final Http2Stream stream;

        Garbage(Http2Stream stream) {
            this.stream = stream;
        }
    }
}
