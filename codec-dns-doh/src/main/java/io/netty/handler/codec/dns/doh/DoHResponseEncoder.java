package io.netty.handler.codec.dns.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encode {@link DnsResponse} into {@link FullHttpResponse}
 */
public final class DoHResponseEncoder extends MessageToMessageEncoder<DnsResponse> {

    private final DnsRecordEncoder recordEncoder;
    private final Map<String, String> httpHeaders;

    /**
     * Creates a new encoder which uses {@linkplain DnsRecordEncoder#DEFAULT the default record encoder},
     * and default HTTP Headers.
     */
    public DoHResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT, new HashMap<String, String>());
    }

    /**
     * Creates a new encoder which uses {@linkplain DnsRecordEncoder#DEFAULT the default record encoder},
     * and custom set of HTTP Headers.
     *
     * @param httpHeaders HTTP Headers to be added with {@link FullHttpResponse}
     */
    public DoHResponseEncoder(Map<String, String> httpHeaders) {
        this(DnsRecordEncoder.DEFAULT, httpHeaders);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder},
     * and custom set of HTTP Headers.
     *
     * @param recordEncoder DNS Record Encoder
     * @param httpHeaders   HTTP Headers to be added with {@link FullHttpResponse}
     */
    public DoHResponseEncoder(DnsRecordEncoder recordEncoder, Map<String, String> httpHeaders) {
        this.recordEncoder = recordEncoder;
        this.httpHeaders = httpHeaders;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsResponse response, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();

        try {
            buildDnsResponseBuf(response, buf);
        } catch (Exception ex) {
            buf.release();
            throw ex;
        }

        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                buf);
        fullHttpResponse.headers()
                .add(HttpHeaderNames.CONTENT_TYPE, "application/dns-message")
                .add(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());

        for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
            fullHttpResponse.headers().add(entry.getKey(), entry.getValue());
        }
    }

    private void buildDnsResponseBuf(DnsResponse response, ByteBuf buf) throws Exception {
        encodeHeader(response, buf);
        encodeQuestions(response, buf);
        encodeRecords(response, DnsSection.ANSWER, buf);
        encodeRecords(response, DnsSection.AUTHORITY, buf);
        encodeRecords(response, DnsSection.ADDITIONAL, buf);
    }

    /**
     * Encodes the header that is always 12 bytes long.
     *
     * @param response the response header being encoded
     * @param buf      the buffer the encoded data should be written to
     */
    private void encodeHeader(DnsResponse response, ByteBuf buf) {
        buf.writeShort(response.id());
        int flags = 32768;
        flags |= (response.opCode().byteValue() & 0xFF) << 11;
        if (response.isAuthoritativeAnswer()) {
            flags |= 1 << 10;
        }
        if (response.isTruncated()) {
            flags |= 1 << 9;
        }
        if (response.isRecursionDesired()) {
            flags |= 1 << 8;
        }
        if (response.isRecursionAvailable()) {
            flags |= 1 << 7;
        }
        flags |= response.z() << 4;
        flags |= response.code().intValue();
        buf.writeShort(flags);
        buf.writeShort(response.count(DnsSection.QUESTION));
        buf.writeShort(response.count(DnsSection.ANSWER));
        buf.writeShort(response.count(DnsSection.AUTHORITY));
        buf.writeShort(response.count(DnsSection.ADDITIONAL));
    }

    private void encodeQuestions(DnsResponse response, ByteBuf buf) throws Exception {
        final int count = response.count(DnsSection.QUESTION);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeQuestion((DnsQuestion) response.recordAt(DnsSection.QUESTION, i), buf);
        }
    }

    private void encodeRecords(DnsResponse response, DnsSection section, ByteBuf buf) throws Exception {
        final int count = response.count(section);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeRecord(response.recordAt(section, i), buf);
        }
    }
}
