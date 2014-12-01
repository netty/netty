/*
 * Copyright 2014 The Netty Project
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
package io.netty.example.dns.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsType;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple DNS client. Note that this is for <i>demonstration</i> purposes - in
 * particular in a production implementation you would want to deal with
 * failures where the remote server never responds at all, fail over across
 * multiple servers; and sequentially incrementing IDs are a bad idea.
 * <p>
 * Caching is left as an exercise for the reader.
 */
public class DnsClient {

    // Map of Callbacks for IDs.  Note that in a production server you would
    // want to track when a callback was submitted, and discard callbacks
    // that have been waiting for an answer for too long - this will leak
    // if the remote server does not respond at all.
    private final ConcurrentMap<Integer, Callback> callbacks = new ConcurrentHashMap<Integer, Callback>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Channel channel;
    private final DnsQueryEncoder enc = new DnsQueryEncoder();
    private final String remoteServer;
    private final int remotePort;

    public DnsClient(EventLoopGroup group) throws InterruptedException {
        this(group, "8.8.8.8"); // Google Public DNS
    }

    public DnsClient(EventLoopGroup group, String remoteDnsServer) throws InterruptedException {
        this(group, remoteDnsServer, 53);
    }

    public DnsClient(EventLoopGroup group, String remoteDnsServer, int port) throws InterruptedException {
        remoteServer = remoteDnsServer;
        remotePort = port;
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new DnsClientInboundHandler(callbacks));

        channel = b.bind(0).sync().channel();
    }

    private int nextId() {
        // Note that sequentially incrementing IDs are not a good idea
        // in production, since guessable IDs can aid in DNS cache poisoning
        // attacks.
        return ids.getAndIncrement();
    }

    public int query(String queryString, Callback callback) throws Exception {
        DnsQuestion q = new DnsQuestion(queryString, DnsType.A, DnsClass.IN);
        DnsQuery query = new DnsQuery(nextId(), new InetSocketAddress(remoteServer, remotePort));
        query.addQuestion(q);
        return query(query, callback);
    }

    public int query(Collection<DnsQuestion> questions, Callback callback) throws Exception {
        DnsQuery query = new DnsQuery(nextId(), new InetSocketAddress(remoteServer, remotePort));
        for (DnsQuestion q : questions) {
            query.addQuestion(q);
        }
        return query(query, callback);
    }

    private int query(final DnsQuery query, final Callback callback) throws Exception {
        DatagramPacket pkt = enc.encode(channel.pipeline().firstContext(), query);
        callbacks.put(query.header().id(), callback);
        channel.writeAndFlush(pkt);
        return query.header().id();
    }

    public abstract static class Callback {

        public abstract void onResponseReceived(DnsResponse response) throws Exception;
    }

    public static void main(String[] ignored) throws InterruptedException, Exception {
        DnsClient client = new DnsClient(new NioEventLoopGroup());
        final CountDownLatch latch = new CountDownLatch(1);
        client.query("timboudreau.com", new Callback() {

            @Override
            public void onResponseReceived(DnsResponse response) throws Exception {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.MINUTES);
    }
}
