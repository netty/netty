/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.example.http2.helloworld.client;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http2.HttpConversionUtil;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.util.AbstractMap.SimpleEntry;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Process {@link FullHttpResponse} translated from HTTP/2 frames
 */
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final Map<Integer, Entry<Future<Void>, Promise<Void>>> streamidPromiseMap;

    public HttpResponseHandler() {
        // Use a concurrent map because we add and iterate from the main thread (just for the purposes of the example),
        // but Netty also does a get on the map when messages are received in a EventLoop thread.
        streamidPromiseMap = new ConcurrentHashMap<>();
    }

    /**
     * Create an association between an anticipated response stream id and a {@link Promise}
     *
     * @param streamId The stream for which a response is expected
     * @param writeFuture A future that represent the request write operation
     * @param promise The promise object that will be used to wait/notify events
     * @return The previous object associated with {@code streamId}
     * @see HttpResponseHandler#awaitResponses(long, TimeUnit)
     */
    public Entry<Future<Void>, Promise<Void>> put(int streamId, Future<Void> writeFuture, Promise<Void> promise) {
        return streamidPromiseMap.put(streamId, new SimpleEntry<>(writeFuture, promise));
    }

    /**
     * Wait (sequentially) for a time duration for each anticipated response
     *
     * @param timeout Value of time to wait for each response
     * @param unit Units associated with {@code timeout}
     * @see HttpResponseHandler#put(int, Future, Promise)
     */
    public void awaitResponses(long timeout, TimeUnit unit) {
        Iterator<Entry<Integer, Entry<Future<Void>, Promise<Void>>>> itr = streamidPromiseMap.entrySet().iterator();
        while (itr.hasNext()) {
            Entry<Integer, Entry<Future<Void>, Promise<Void>>> entry = itr.next();
            Future<Void> writeFuture = entry.getValue().getKey();
            if (!writeFuture.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting to write for stream id " + entry.getKey());
            }
            if (writeFuture.isFailed()) {
                throw new RuntimeException(writeFuture.cause());
            }
            Promise<Void> promise = entry.getValue().getValue();
            if (!promise.asFuture().awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting for response on stream id " + entry.getKey());
            }
            if (promise.isFailed()) {
                throw new RuntimeException(promise.cause());
            }
            System.out.println("---Stream id: " + entry.getKey() + " received---");
            itr.remove();
        }
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            System.err.println("HttpResponseHandler unexpected message received: " + msg);
            return;
        }

        Entry<Future<Void>, Promise<Void>> entry = streamidPromiseMap.get(streamId);
        if (entry == null) {
            System.err.println("Message received for unknown stream id " + streamId);
        } else {
            // Do stuff with the message (for now just print it)
            Buffer content = msg.payload();
            if (content.readableBytes() > 0) {
                int contentLength = content.readableBytes();
                byte[] arr = new byte[contentLength];
                content.copyInto(content.readerOffset(), arr, 0, contentLength);
                content.skipReadable(contentLength);
                System.out.println(new String(arr, 0, contentLength, CharsetUtil.UTF_8));
            }

            entry.getValue().setSuccess(null);
        }
    }
}
