/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns.resolver;

import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseException;
import io.netty.util.NetUtil;
import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.messages.ResourceRecordModifier;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.protocol.DnsUdpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpEncoder;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class AsynchronousDnsResolverTest {
    // bytes representation of ::1
    private static final byte[] IP6_BYTES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };

    private final ChannelFactory<DatagramChannel> factory = new ChannelFactory<DatagramChannel>() {
        @Override
        public DatagramChannel newChannel(EventLoop eventLoop) {
            return new NioDatagramChannel(eventLoop);
        }
    };

    private TestDnsServer dnsServer;
    private EventLoopGroup group;

    @Before
    public void before() {
        if (dnsServer != null) {
            dnsServer.stop();
        }
        group = new NioEventLoopGroup(1);
    }

    @After
    public void after() {
        if (dnsServer != null) {
            dnsServer.stop();
        }
        group.shutdownGracefully();
    }

    @Test
    public void testResolveA() throws Exception {
        final String ip = "10.0.0.1";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.A);
                rm.put(DnsAttribute.IP_ADDRESS, ip);
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<Inet4Address> addresses = dns.resolve4("netty.io").get();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals(ip, addresses.get(0).getHostAddress());
    }

    @Test
    public void testResolveAAAA() throws Exception {
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.AAAA);
                rm.put(DnsAttribute.IP_ADDRESS, "::1");
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<Inet6Address> addresses = dns.resolve6("google.com").get();
        Assert.assertEquals(1, addresses.size());
        Assert.assertArrayEquals(IP6_BYTES, addresses.get(0).getAddress());
    }

    @Test
    public void testResolveMx() throws Exception {
        final String mxRecord = "mail.vertx.io";
        final int prio = 10;
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.MX);
                rm.put(DnsAttribute.MX_PREFERENCE, String.valueOf(prio));
                rm.put(DnsAttribute.DOMAIN_NAME, mxRecord);
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<MailExchangerRecord> records = dns.resolveMx("netty.io").get();
        Assert.assertEquals(1, records.size());
        MailExchangerRecord record = records.get(0);
        Assert.assertEquals(prio, record.priority());
        Assert.assertEquals(mxRecord, record.name());
    }

    @Test
    public void testResolveTxt() throws Exception {
        final String txt = "vertx is awesome";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.TXT);
                rm.put(DnsAttribute.CHARACTER_STRING, txt);
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<List<String>> txts = dns.resolveTxt("netty.io").get();
        Assert.assertEquals(1, txts.size());
        List<String> textRecord = txts.get(0);
        Assert.assertEquals(1, textRecord.size());
        Assert.assertEquals(txt, textRecord.get(0));
    }

    @Test
    public void testResolveNs() throws Exception {
        final String ns = "ns.netty.io";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.NS);
                rm.put(DnsAttribute.DOMAIN_NAME, ns);
                set.add(rm.getEntry());
                return set;
            }
        }));
        List<String> records = dns.resolveNs("netty.io").get();
        Assert.assertEquals(1, records.size());
        Assert.assertEquals(ns, records.get(0));
    }

    @Test
    public void testResolveCname() throws Exception {
        final String cname = "cname.netty.io";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.CNAME);
                rm.put(DnsAttribute.DOMAIN_NAME, cname);
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<String> records = dns.resolveCname("netty.io").get();
        Assert.assertEquals(1, records.size());
        Assert.assertEquals(cname, records.get(0));
    }

    @Test
    public void testResolveSrv() throws Exception {
        final int priority = 10;
        final int weight = 1;
        final int port = 80;
        final String target = "netty.io";

        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.SRV);
                rm.put(DnsAttribute.SERVICE_PRIORITY, String.valueOf(priority));
                rm.put(DnsAttribute.SERVICE_WEIGHT, String.valueOf(weight));
                rm.put(DnsAttribute.SERVICE_PORT, String.valueOf(port));
                rm.put(DnsAttribute.DOMAIN_NAME, target);
                set.add(rm.getEntry());
                return set;
            }
        }));

        List<ServiceRecord> records = dns.resolveSrv("netty.io").get();
        Assert.assertEquals(1, records.size());
        ServiceRecord record = records.get(0);

        Assert.assertEquals(priority, record.priority());
        Assert.assertEquals(weight, record.weight());
        Assert.assertEquals(port, record.port());
        Assert.assertEquals(target, record.target());
    }

    @Test
    public void testLookup4() throws Exception {
        final String ip = "10.0.0.1";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.A);
                rm.put(DnsAttribute.IP_ADDRESS, ip);
                set.add(rm.getEntry());
                return set;
            }
        }));

        InetAddress address = dns.lookup("netty.io", InternetProtocolFamily.IPv4).get();
        Assert.assertEquals(ip, address.getHostAddress());
    }

    @Test
    public void testLookup6() throws Exception {
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.AAAA);
                rm.put(DnsAttribute.IP_ADDRESS, "::1");
                set.add(rm.getEntry());
                return set;
            }
        }));

        InetAddress address = dns.lookup("netty.io", InternetProtocolFamily.IPv6).get();
        Assert.assertArrayEquals(IP6_BYTES, address.getAddress());
    }

    @Test
    public void testLookup() throws Exception {
        final String ip = "10.0.0.1";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.A);
                rm.put(DnsAttribute.IP_ADDRESS, ip);
                set.add(rm.getEntry());
                return set;
            }
        }));
        InetAddress address = dns.lookup("netty.io").get();
        Assert.assertEquals(ip, address.getHostAddress());
    }

    @Test
    public void testLookupNonExisting() throws Exception {
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                return null;
            }
        }));

        try {
            dns.lookup("notexists.netty.io").get();
            Assert.fail();
        } catch (ExecutionException e) {
            DnsResponseException ex = (DnsResponseException) e.getCause();
            Assert.assertEquals(DnsResponseCode.NXDOMAIN, ex.responseCode());
        }
    }

    @Test
    public void testReverse() throws Exception {
        final String ptr = "ptr.netty.io";
        AsynchronousDnsResolver dns = prepare(new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> set = new HashSet<ResourceRecord>();

                ResourceRecordModifier rm = createModifier();
                rm.setDnsType(RecordType.PTR);
                rm.put(DnsAttribute.DOMAIN_NAME, ptr);
                set.add(rm.getEntry());
                return set;
            }
        }));
        Assert.assertEquals(ptr, dns.reverse("10.0.0.1").get());
    }

    private AsynchronousDnsResolver prepare(TestDnsServer server) throws Exception {
        dnsServer = server;
        dnsServer.start();
        InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
        return new AsynchronousDnsResolver(factory, group, addr);
    }

    private static ResourceRecordModifier createModifier() {
        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.netty.io");
        rm.setDnsTtl(100);
        return rm;
    }

    private static final class TestDnsServer extends DnsServer {

        private final RecordStore store;

        private TestDnsServer(RecordStore store) {
            this.store = store;
        }

        @Override
        public void start() throws IOException {
            UdpTransport transport = new UdpTransport(NetUtil.LOCALHOST.getHostAddress(), 0);
            setTransports(transport);

            DatagramAcceptor acceptor = transport.getAcceptor();

            acceptor.setHandler(new DnsProtocolHandler(this, store) {
                @Override
                public void sessionCreated(IoSession session) throws Exception {
                    // Use our own codec to support AAAA testing
                    session.getFilterChain().addFirst("codec",
                            new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
                }
            });

            // Allow the port to be reused even if the socket is in TIME_WAIT state
            ((DatagramSessionConfig) acceptor.getSessionConfig()).setReuseAddress(true);

            // Start the listener
            acceptor.bind();
        }

        private final class TestDnsProtocolUdpCodecFactory implements ProtocolCodecFactory {
            private final DnsMessageEncoder encoder = new DnsMessageEncoder();
            private final TestAAAARecordEncoder recordEncoder = new TestAAAARecordEncoder();

            @Override
            public ProtocolEncoder getEncoder(IoSession session) throws Exception {
                return new DnsUdpEncoder() {

                    @Override
                    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
                        IoBuffer buf = IoBuffer.allocate(1024);
                        DnsMessage dnsMessage = (DnsMessage) message;
                        encoder.encode(buf, dnsMessage);
                        for (ResourceRecord record: dnsMessage.getAnswerRecords()) {
                            // This is a hack to allow to also test for AAAA resolution as DnsMessageEncoder
                            // does not support it and it is hard to extend, because the interesting methods
                            // are private...
                            // In case of RecordType.AAAA we need to encode the RecordType by ourself
                            if (record.getRecordType() == RecordType.AAAA) {
                                try {
                                    recordEncoder.put(buf, record);
                                } catch (IOException e) {
                                    // Should never happen
                                    throw new IllegalStateException(e);
                                }
                            }
                        }
                        buf.flip();

                        out.write(buf);
                    }
                };
            }

            @Override
            public ProtocolDecoder getDecoder(IoSession session) throws Exception {
                return new DnsUdpDecoder();
            }

            private final class TestAAAARecordEncoder extends ResourceRecordEncoder {
                @Override
                protected void putResourceRecordData(IoBuffer ioBuffer, ResourceRecord resourceRecord) {
                    if (!"::1".equals(resourceRecord.get(DnsAttribute.IP_ADDRESS))) {
                        throw new IllegalStateException("Only supposed to be used with IPV6 address of ::1");
                    }
                    // encode the ::1
                    ioBuffer.put(IP6_BYTES);
                }
            }
        }
    }
}
