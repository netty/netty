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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.CharsetUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Utilities for SVCB records (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>).
 */
final class DnsSvcbUtil {

    /**
     * Encodes SVCB RDATA (SvcPriority, TargetName, SvcParams) into {@code out}.
     * <p>
     * <strong>No checks are done when encoding:</strong> the params are written in the given order, and
     * duplicate keys or records that are not self-consistent are written as is, so the result may be invalid
     * SVCB data. This is meant for tests; use {@link DefaultDnsRecordEncoder} to encode valid records.
     * <p>
     * The target name is always written uncompressed.
     *
     * @param priority the SvcPriority
     * @param targetName the TargetName
     * @param params the SvcParams, written in this order
     * @param out the buffer to write to
     */
    static void encodeRdata(int priority, String targetName, List<SvcParam> params, ByteBuf out) {
        out.writeShort(priority);
        DnsCodecUtil.encodeDomainName(targetName, out);
        for (SvcParam param : params) {
            param.encode(out);
        }
    }

    /**
     * Decodes an SVCB or HTTPS record from its RDATA, located at {@code [offset, offset + length)} in {@code in}.
     * The reader index of {@code in} is not modified.
     * <p>
     * The RDATA must be well-formed according to RFC 9460: the TargetName must not be compressed, SvcParamKeys
     * must be in strictly increasing order, and each SvcParamValue must have the expected format for its key.
     * Self-consistency between SvcParams is not checked.
     *
     * @throws CorruptedFrameException if the RDATA is malformed
     */
    static DefaultDnsSvcbRecord decodeRecord(String name, DnsRecordType type, int dnsClass, long timeToLive,
                                             ByteBuf in, int offset, int length) {
        return decodeRecord(name, type, dnsClass, timeToLive, in, offset, length, SvcbDecoderConfig.DEFAULT_MAX_PARAMS,
                SvcbDecoderConfig.DEFAULT_MAX_ALPN_IDS, SvcbDecoderConfig.DEFAULT_MAX_HINT_ADDRESSES);
    }

    /**
     * Decodes the RDATA of a {@link DnsRawRecord} of type {@link DnsRecordType#SVCB} or {@link DnsRecordType#HTTPS},
     * with the name, type, class and TTL of {@code record}. Its content is only read.
     *
     * @throws IllegalArgumentException if {@code record} is not of type {@link DnsRecordType#SVCB} or
     *                                  {@link DnsRecordType#HTTPS}
     * @throws CorruptedFrameException if the RDATA is malformed or exceeds a limit
     */
    static DefaultDnsSvcbRecord decodeRecord(DnsRawRecord record, int maxParams, int maxAlpnIds,
                                             int maxHintAddresses) {
        checkNotNull(record, "record");
        if (record.type() != DnsRecordType.SVCB && record.type() != DnsRecordType.HTTPS) {
            throw new IllegalArgumentException("record type must be SVCB or HTTPS but was " + record.type());
        }
        ByteBuf content = record.content();
        return decodeRecord(record.name(), record.type(), record.dnsClass(), record.timeToLive(), content,
                content.readerIndex(), content.readableBytes(), maxParams, maxAlpnIds, maxHintAddresses);
    }

    /**
     * Decodes an SVCB or HTTPS record like {@link #decodeRecord(String, DnsRecordType, int, long, ByteBuf, int, int)},
     * with the limits of a {@link SvcbDecoderConfig}.
     *
     * @throws CorruptedFrameException if the RDATA is malformed or exceeds a limit
     */
    static DefaultDnsSvcbRecord decodeRecord(String name, DnsRecordType type, int dnsClass, long timeToLive,
                                             ByteBuf in, int offset, int length, int maxParams, int maxAlpnIds,
                                             int maxHintAddresses) {
        final int end = offset + length;
        if (length < 3) {
            throw new CorruptedFrameException("SVCB RDATA is too short: " + length);
        }
        final int priority = in.getUnsignedShort(offset);

        int index = offset + 2;
        final StringBuilder target = new StringBuilder();
        for (;;) {
            if (index >= end) {
                throw new CorruptedFrameException("truncated SVCB TargetName");
            }
            final int labelLength = in.getUnsignedByte(index++);
            if (labelLength == 0) {
                break;
            }
            if ((labelLength & 0xc0) != 0) {
                throw new CorruptedFrameException("SVCB TargetName must not be compressed or use an invalid label");
            }
            if (end - index < labelLength) {
                throw new CorruptedFrameException("truncated SVCB TargetName");
            }
            target.append(in.toString(index, labelLength, CharsetUtil.UTF_8)).append('.');
            index += labelLength;
            if (index - offset - 2 > DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH) {
                throw new CorruptedFrameException(
                        "SVCB TargetName must be <= " + DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH + " octets");
            }
        }
        final String targetName = target.length() == 0 ? DefaultDnsRecordDecoder.ROOT : target.toString();

        final List<SvcParam> params = new ArrayList<SvcParam>();
        int previousKey = -1;
        while (index < end) {
            if (end - index < 4) {
                throw new CorruptedFrameException("the end of the SVCB RDATA occurs within a SvcParam");
            }
            final int key = in.getUnsignedShort(index);
            final int valueLength = in.getUnsignedShort(index + 2);
            index += 4;
            if (end - index < valueLength) {
                throw new CorruptedFrameException("the end of the SVCB RDATA occurs within a SvcParam");
            }
            if (key <= previousKey) {
                throw new CorruptedFrameException(
                        "SvcParamKeys are not in strictly increasing order: " + previousKey + ", " + key);
            }
            if (params.size() == maxParams) {
                throw new CorruptedFrameException("more than " + maxParams + " SvcParams");
            }
            params.add(decodeParam(key, in, index, valueLength, maxAlpnIds, maxHintAddresses));
            previousKey = key;
            index += valueLength;
        }

        return new DefaultDnsSvcbRecord(name, type, dnsClass, timeToLive, priority, targetName, params);
    }

    private static SvcParam decodeParam(int key, ByteBuf in, int offset, int length, int maxAlpnIds,
                                        int maxHintAddresses) {
        final int end = offset + length;
        switch (key) {
            case SvcParam.MANDATORY: {
                if (length == 0 || length % 2 != 0) {
                    throw new CorruptedFrameException("invalid mandatory length: " + length);
                }
                final List<Integer> keys = new ArrayList<Integer>(length / 2);
                int previous = -1;
                for (int i = offset; i < end; i += 2) {
                    final int mandatoryKey = in.getUnsignedShort(i);
                    if (mandatoryKey == SvcParam.MANDATORY) {
                        throw new CorruptedFrameException("mandatory must not list itself");
                    }
                    if (mandatoryKey <= previous) {
                        throw new CorruptedFrameException("mandatory keys are not in strictly increasing order");
                    }
                    keys.add(mandatoryKey);
                    previous = mandatoryKey;
                }
                return new SvcParam.Mandatory(keys);
            }
            case SvcParam.ALPN: {
                if (length == 0) {
                    throw new CorruptedFrameException("alpn must not be empty");
                }
                final List<ProtocolId> protocols = new ArrayList<ProtocolId>();
                int index = offset;
                while (index < end) {
                    final int idLength = in.getUnsignedByte(index++);
                    if (idLength == 0 || idLength > end - index) {
                        throw new CorruptedFrameException("invalid alpn-id length: " + idLength);
                    }
                    if (protocols.size() == maxAlpnIds) {
                        throw new CorruptedFrameException("more than " + maxAlpnIds + " alpn-ids");
                    }
                    protocols.add(ProtocolId.valueOf(in, index, idLength));
                    index += idLength;
                }
                return new SvcParam.Alpn(protocols);
            }
            case SvcParam.NO_DEFAULT_ALPN:
                if (length != 0) {
                    throw new CorruptedFrameException("no-default-alpn must be empty but was " + length);
                }
                return SvcParam.NoDefaultAlpn.INSTANCE;
            case SvcParam.PORT:
                if (length != 2) {
                    throw new CorruptedFrameException("invalid port length: " + length);
                }
                return new SvcParam.Port(in.getUnsignedShort(offset));
            case SvcParam.IPV4HINT: {
                if (length == 0 || length % 4 != 0) {
                    throw new CorruptedFrameException("invalid ipv4hint length: " + length);
                }
                if (length / 4 > maxHintAddresses) {
                    throw new CorruptedFrameException("more than " + maxHintAddresses + " ipv4hint addresses");
                }
                final List<Inet4Address> addresses = new ArrayList<Inet4Address>(length / 4);
                for (int i = offset; i < end; i += 4) {
                    addresses.add((Inet4Address) toAddress(in, i, 4));
                }
                return new SvcParam.Ipv4Hint(addresses);
            }
            case SvcParam.ECH:
                return new SvcParam.Ech(ByteBufUtil.getBytes(in, offset, length));
            case SvcParam.IPV6HINT: {
                if (length == 0 || length % 16 != 0) {
                    throw new CorruptedFrameException("invalid ipv6hint length: " + length);
                }
                if (length / 16 > maxHintAddresses) {
                    throw new CorruptedFrameException("more than " + maxHintAddresses + " ipv6hint addresses");
                }
                final List<Inet6Address> addresses = new ArrayList<Inet6Address>(length / 16);
                for (int i = offset; i < end; i += 16) {
                    addresses.add((Inet6Address) toAddress(in, i, 16));
                }
                return new SvcParam.Ipv6Hint(addresses);
            }
            default:
                return new SvcParam.Unknown(key, ByteBufUtil.getBytes(in, offset, length));
        }
    }

    private static InetAddress toAddress(ByteBuf in, int offset, int length) {
        final byte[] address = ByteBufUtil.getBytes(in, offset, length);
        try {
            return length == 4 ? InetAddress.getByAddress(address) : Inet6Address.getByAddress(null, address, -1);
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException(e);
        }
    }

    private DnsSvcbUtil() { }
}
