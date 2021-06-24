package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

@ChannelHandler.Sharable
public final class TcpDnsResponseEncoder extends MessageToMessageEncoder<DnsResponse> {
    private final DnsRecordEncoder encoder;

    public TcpDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    public TcpDnsResponseEncoder(DnsRecordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsResponse response, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().ioBuffer(1024);
        boolean success = false;
        try {
            buf.writerIndex(buf.writerIndex() + 2);
            encodeHeader(response, buf);
            this.encodeQuestions(response, buf);
            this.encodeRecords(response, DnsSection.ANSWER, buf);
            this.encodeRecords(response, DnsSection.AUTHORITY, buf);
            this.encodeRecords(response, DnsSection.ADDITIONAL, buf);
            buf.setShort(0, buf.readableBytes() - 2);
            success = true;
        } finally {
            if (!success) {
                buf.release();
            }
        }
        out.add(buf);
    }

    private static void encodeHeader(DnsResponse response, ByteBuf buf) {
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
        int count = response.count(DnsSection.QUESTION);
        for (int i = 0; i < count; ++i) {
            this.encoder.encodeQuestion(response.<DnsQuestion>recordAt(DnsSection.QUESTION, i), buf);
        }
    }

    private void encodeRecords(DnsResponse response, DnsSection section, ByteBuf buf) throws Exception {
        int count = response.count(section);
        for (int i = 0; i < count; ++i) {
            this.encoder.encodeRecord(response.recordAt(section, i), buf);
        }
    }
}
