/*
 * Copyright 2026 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A DNS server on the loopback interface that answers from records built with dnsjava, which supports record types
 * the ApacheDS based {@link TestDnsServer} does not, such as HTTPS. Answers follow CNAMEs within the response.
 * Records added with {@link #addRaw} are appended to the answer section as given, which allows sending RDATA that
 * dnsjava would refuse or fix, such as malformed SVCB/HTTPS records.
 */
final class DnsJavaTestServer implements Closeable {

    private final Map<String, List<Record>> records = new ConcurrentHashMap<String, List<Record>>();
    private final Map<String, List<byte[]>> rawRecords = new ConcurrentHashMap<String, List<byte[]>>();
    private final List<SOARecord> soaRecords = new CopyOnWriteArrayList<SOARecord>();
    private final List<DatagramPacket> heldQueries = new ArrayList<DatagramPacket>();
    private final List<Integer> receivedTypes = new ArrayList<Integer>();
    private final List<String> receivedNames = new ArrayList<String>();
    private volatile int holdUntil;
    private final Set<Integer> droppedTypes = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Map<Integer, Integer> failedTypes = new ConcurrentHashMap<Integer, Integer>();
    private final Set<String> heldKeys = new HashSet<String>();
    private final List<DatagramPacket> heldAnswers = new ArrayList<DatagramPacket>();
    private final Channel channel;

    DnsJavaTestServer(EventLoopGroup group) throws InterruptedException {
        channel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
                        final Record question = new Message(ByteBufUtil.getBytes(packet.content())).getQuestion();
                        final int type = question.getType();
                        synchronized (heldQueries) {
                            receivedTypes.add(type);
                            receivedNames.add(question.getName().toString().toLowerCase(Locale.ROOT));
                            if (droppedTypes.contains(type)) {
                                return;
                            }
                            if (heldKeys.contains(key(question.getName(), type))) {
                                heldAnswers.add(packet.retain());
                                return;
                            }
                            if (holdUntil > 0) {
                                heldQueries.add(packet.retain());
                                if (heldQueries.size() < holdUntil) {
                                    return;
                                }
                                for (DatagramPacket held : heldQueries) {
                                    reply(ctx, held);
                                    held.release();
                                }
                                heldQueries.clear();
                                holdUntil = 0;
                                return;
                            }
                        }
                        reply(ctx, packet);
                    }

                    private void reply(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
                        DnsJavaTestServer.this.reply(ctx.channel(), packet);
                    }
                })
                .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();
    }

    InetSocketAddress localAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    void add(Record record) {
        List<Record> list = records.get(key(record.getName(), record.getType()));
        if (list == null) {
            list = new ArrayList<Record>();
            records.put(key(record.getName(), record.getType()), list);
        }
        list.add(record);
    }

    /**
     * Adds a record whose RDATA is sent exactly as given, with class IN and a TTL of 300.
     */
    void addRaw(String owner, int type, byte[] rdata) throws TextParseException {
        Name name = Name.fromString(owner);
        byte[] ownerWire = name.toWireCanonical();
        ByteBuf rr = Unpooled.buffer();
        rr.writeBytes(ownerWire).writeShort(type).writeShort(DClass.IN).writeInt(300).writeShort(rdata.length)
                .writeBytes(rdata);
        String key = key(name, type);
        List<byte[]> list = rawRecords.get(key);
        if (list == null) {
            list = new ArrayList<byte[]>();
            rawRecords.put(key, list);
        }
        list.add(ByteBufUtil.getBytes(rr));
        rr.release();
    }

    /**
     * Holds the answers until {@code queries} queries have been received, then answers all of them and stops
     * holding. A resolution that sends its queries one after another then never completes.
     */
    void holdUntil(int queries) {
        holdUntil = queries;
    }

    /**
     * Adds the SOA of the zone {@code zone}, sent in the authority section of the answers without records for a name
     * of the zone.
     */
    void addSoa(String zone, long ttl, long minimum) throws TextParseException {
        Name name = Name.fromString(zone);
        soaRecords.add(new SOARecord(name, DClass.IN, ttl, Name.fromString("ns." + zone),
                Name.fromString("hostmaster." + zone), 1, 3600, 600, 86400, minimum));
    }

    /**
     * Holds the answers to the queries of {@code name} and {@code type} until {@link #release()}.
     */
    void hold(String name, int type) throws TextParseException {
        synchronized (heldQueries) {
            heldKeys.add(key(Name.fromString(name), type));
        }
    }

    /**
     * Stops holding answers, and answers the held queries.
     */
    void release() throws Exception {
        synchronized (heldQueries) {
            heldKeys.clear();
            for (DatagramPacket held : heldAnswers) {
                reply(channel, held);
                held.release();
            }
            heldAnswers.clear();
        }
    }

    /**
     * Returns the types of the queries received so far, in arrival order.
     */
    List<Integer> receivedTypes() {
        synchronized (heldQueries) {
            return new ArrayList<Integer>(receivedTypes);
        }
    }

    /**
     * Returns the names of the queries received so far, in arrival order.
     */
    List<String> receivedNames() {
        synchronized (heldQueries) {
            return new ArrayList<String>(receivedNames);
        }
    }

    /**
     * Returns the types of the queries received after the first {@code count} ones, in arrival order.
     */
    List<Integer> receivedTypesSince(int count) {
        synchronized (heldQueries) {
            return new ArrayList<Integer>(receivedTypes.subList(count, receivedTypes.size()));
        }
    }

    /**
     * Returns the names of the queries of {@code type} received so far, in arrival order.
     */
    List<String> receivedNames(int type) {
        synchronized (heldQueries) {
            List<String> names = new ArrayList<String>();
            for (int i = 0; i < receivedTypes.size(); i++) {
                if (receivedTypes.get(i) == type) {
                    names.add(receivedNames.get(i));
                }
            }
            return names;
        }
    }

    /**
     * Never answers the queries of {@code type}, so that they time out.
     */
    void drop(int type) {
        droppedTypes.add(type);
    }

    /**
     * Answers the queries of {@code type} with {@code rcode} and no records.
     */
    void fail(int type, int rcode) {
        failedTypes.put(type, rcode);
    }

    /**
     * Removes the records and stops dropping or failing queries.
     */
    void clear() {
        records.clear();
        rawRecords.clear();
        soaRecords.clear();
        droppedTypes.clear();
        failedTypes.clear();
    }

    private void reply(Channel channel, DatagramPacket packet) throws Exception {
        Message query = new Message(ByteBufUtil.getBytes(packet.content()));
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(answer(query)), packet.sender()));
    }

    private byte[] answer(Message query) {
        Record question = query.getQuestion();
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setFlag(Flags.AA);
        response.getHeader().setRcode(Rcode.NOERROR);
        response.addRecord(question, Section.QUESTION);
        final Integer rcode = failedTypes.get(question.getType());
        if (rcode != null) {
            response.getHeader().setRcode(rcode);
            return response.toWire();
        }

        Name name = question.getName();
        for (int i = 0; i < 8; i++) {
            List<Record> answers = records.get(key(name, question.getType()));
            List<byte[]> raw = rawRecords.get(key(name, question.getType()));
            if (answers != null || raw != null) {
                if (answers != null) {
                    for (Record record : answers) {
                        response.addRecord(record, Section.ANSWER);
                    }
                }
                return raw == null ? response.toWire() : appendAnswers(response.toWire(), raw);
            }
            List<Record> cnames = records.get(key(name, Type.CNAME));
            if (cnames == null) {
                for (SOARecord soa : soaRecords) {
                    if (name.subdomain(soa.getName())) {
                        response.addRecord(soa, Section.AUTHORITY);
                        break;
                    }
                }
                break;
            }
            CNAMERecord cname = (CNAMERecord) cnames.get(0);
            response.addRecord(cname, Section.ANSWER);
            name = cname.getTarget();
        }
        return response.toWire();
    }

    private static byte[] appendAnswers(byte[] message, List<byte[]> rawAnswers) {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeBytes(message);
            for (byte[] rr : rawAnswers) {
                out.writeBytes(rr);
            }
            // ANCOUNT is at offset 6 in the header; the response has no authority or additional records.
            out.setShort(6, out.getUnsignedShort(6) + rawAnswers.size());
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }

    private static String key(Name name, int type) {
        return name.toString().toLowerCase(Locale.ROOT) + '/' + type;
    }

    @Override
    public void close() {
        channel.close().syncUninterruptibly();
        synchronized (heldQueries) {
            for (DatagramPacket held : heldQueries) {
                held.release();
            }
            heldQueries.clear();
            for (DatagramPacket held : heldAnswers) {
                held.release();
            }
            heldAnswers.clear();
        }
    }
}
