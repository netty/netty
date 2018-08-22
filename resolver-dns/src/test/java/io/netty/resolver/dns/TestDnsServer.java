/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.messages.ResourceRecordImpl;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class TestDnsServer extends DnsServer {
    private static final Map<String, byte[]> BYTES = new HashMap<String, byte[]>();
    private static final String[] IPV6_ADDRESSES;

    static {
        BYTES.put("::1", new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        BYTES.put("0:0:0:0:0:0:1:1", new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1});
        BYTES.put("0:0:0:0:0:1:1:1", new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 1});
        BYTES.put("0:0:0:0:1:1:1:1", new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1});
        BYTES.put("0:0:0:1:1:1:1:1", new byte[]{0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});
        BYTES.put("0:0:1:1:1:1:1:1", new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});
        BYTES.put("0:1:1:1:1:1:1:1", new byte[]{0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});
        BYTES.put("1:1:1:1:1:1:1:1", new byte[]{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});

        IPV6_ADDRESSES = BYTES.keySet().toArray(new String[0]);
    }

    private final RecordStore store;

    TestDnsServer(Set<String> domains) {
        this(new TestRecordStore(domains));
    }

    TestDnsServer(RecordStore store) {
        this.store = store;
    }

    @Override
    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(NetUtil.LOCALHOST4, 0);
        UdpTransport transport = new UdpTransport(address.getHostName(), address.getPort());
        setTransports(transport);

        DatagramAcceptor acceptor = transport.getAcceptor();

        acceptor.setHandler(new DnsProtocolHandler(this, store) {
            @Override
            public void sessionCreated(IoSession session) {
                // USe our own codec to support AAAA testing
                session.getFilterChain()
                    .addFirst("codec", new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
            }
        });

        ((DatagramSessionConfig) acceptor.getSessionConfig()).setReuseAddress(true);

        // Start the listener
        acceptor.bind();
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) getTransports()[0].getAcceptor().getLocalAddress();
    }

    protected DnsMessage filterMessage(DnsMessage message) {
        return message;
    }

    protected static ResourceRecord newARecord(String name, String ipAddress) {
        return newAddressRecord(name, RecordType.A, ipAddress);
    }

    protected static ResourceRecord newNsRecord(String dnsname, String domainName) {
        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName(dnsname);
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.NS);
        rm.put(DnsAttribute.DOMAIN_NAME, domainName);
        return rm.getEntry();
    }

    protected static ResourceRecord newAddressRecord(String name, RecordType type, String address) {
        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName(name);
        rm.setDnsTtl(100);
        rm.setDnsType(type);
        rm.put(DnsAttribute.IP_ADDRESS, address);
        return rm.getEntry();
    }

    /**
     * {@link ProtocolCodecFactory} which allows to test AAAA resolution.
     */
    private final class TestDnsProtocolUdpCodecFactory implements ProtocolCodecFactory {
        private final DnsMessageEncoder encoder = new DnsMessageEncoder();
        private final TestAAAARecordEncoder recordEncoder = new TestAAAARecordEncoder();

        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            return new DnsUdpEncoder() {

                @Override
                public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
                    IoBuffer buf = IoBuffer.allocate(1024);
                    DnsMessage dnsMessage = filterMessage((DnsMessage) message);
                    encoder.encode(buf, dnsMessage);
                    for (ResourceRecord record : dnsMessage.getAnswerRecords()) {
                        // This is a hack to allow to also test for AAAA resolution as DnsMessageEncoder
                        // does not support it and it is hard to extend, because the interesting methods
                        // are private...
                        // In case of RecordType.AAAA we need to encode the RecordType by ourselves.
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
        public ProtocolDecoder getDecoder(IoSession session) {
            return new DnsUdpDecoder();
        }

        private final class TestAAAARecordEncoder extends ResourceRecordEncoder {

            @Override
            protected void putResourceRecordData(IoBuffer ioBuffer, ResourceRecord resourceRecord) {
                byte[] bytes = BYTES.get(resourceRecord.get(DnsAttribute.IP_ADDRESS));
                if (bytes == null) {
                    throw new IllegalStateException(resourceRecord.get(DnsAttribute.IP_ADDRESS));
                }
                // encode the ::1
                ioBuffer.put(bytes);
            }
        }
    }

    static final class MapRecordStoreA implements RecordStore {

        private final Map<String, List<String>> domainMap;

        MapRecordStoreA(Set<String> domains, int length) {
            domainMap = new HashMap<String, List<String>>(domains.size());
            for (String domain : domains) {
                List<String> addresses = new ArrayList<String>(length);
                for (int i = 0; i < length; i++) {
                    addresses.add(TestRecordStore.nextIp());
                }
                domainMap.put(domain, addresses);
            }
        }

        MapRecordStoreA(Set<String> domains) {
            this(domains, 1);
        }

        public String getAddress(String domain) {
            return domainMap.get(domain).get(0);
        }

        public List<String> getAddresses(String domain) {
            return domainMap.get(domain);
        }

        @Override
        public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
            String name = questionRecord.getDomainName();
            List<String> addresses = domainMap.get(name);
            if (addresses != null && questionRecord.getRecordType() == RecordType.A) {
                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>();
                for (String address : addresses) {
                    Map<String, Object> attributes = new HashMap<String, Object>();
                    attributes.put(DnsAttribute.IP_ADDRESS.toLowerCase(), address);
                    records.add(new TestResourceRecord(name, questionRecord.getRecordType(), attributes));
                }
                return records;
            }
            return null;
        }
    }

    private static final class TestRecordStore implements RecordStore {
        private static final int[] NUMBERS = new int[254];
        private static final char[] CHARS = new char[26];

        static {
            for (int i = 0; i < NUMBERS.length; i++) {
                NUMBERS[i] = i + 1;
            }

            for (int i = 0; i < CHARS.length; i++) {
                CHARS[i] = (char) ('a' + i);
            }
        }

        private static int index(int arrayLength) {
            return Math.abs(PlatformDependent.threadLocalRandom().nextInt()) % arrayLength;
        }

        private static String nextDomain() {
            return CHARS[index(CHARS.length)] + ".netty.io";
        }

        private static String nextIp() {
            return ipPart() + "." + ipPart() + '.' + ipPart() + '.' + ipPart();
        }

        private static int ipPart() {
            return NUMBERS[index(NUMBERS.length)];
        }

        private static String nextIp6() {
            return IPV6_ADDRESSES[index(IPV6_ADDRESSES.length)];
        }

        private final Set<String> domains;

        private TestRecordStore(Set<String> domains) {
            this.domains = domains;
        }

        @Override
        public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
            String name = questionRecord.getDomainName();
            if (domains.contains(name)) {
                Map<String, Object> attr = new HashMap<String, Object>();
                switch (questionRecord.getRecordType()) {
                    case A:
                        do {
                            attr.put(DnsAttribute.IP_ADDRESS.toLowerCase(Locale.US), nextIp());
                        } while (PlatformDependent.threadLocalRandom().nextBoolean());
                        break;
                    case AAAA:
                        do {
                            attr.put(DnsAttribute.IP_ADDRESS.toLowerCase(Locale.US), nextIp6());
                        } while (PlatformDependent.threadLocalRandom().nextBoolean());
                        break;
                    case MX:
                        int priority = 0;
                        do {
                            attr.put(DnsAttribute.DOMAIN_NAME.toLowerCase(Locale.US), nextDomain());
                            attr.put(DnsAttribute.MX_PREFERENCE.toLowerCase(Locale.US), String.valueOf(++priority));
                        } while (PlatformDependent.threadLocalRandom().nextBoolean());
                        break;
                    default:
                        return null;
                }
                return Collections.<ResourceRecord>singleton(
                        new TestResourceRecord(name, questionRecord.getRecordType(), attr));
            }
            return null;
        }
    }

    static final class TestResourceRecord extends ResourceRecordImpl {

        TestResourceRecord(String domainName, RecordType recordType, Map<String, Object> attributes) {
            super(domainName, recordType, RecordClass.IN, 100, attributes);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }
    }
}
