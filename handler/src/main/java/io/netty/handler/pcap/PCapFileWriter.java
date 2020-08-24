package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class PCapFileWriter implements Closeable {
    private final long myStartTime = System.nanoTime();
    private final FileOutputStream fileOutputStream;

    public PCapFileWriter(File file) throws IOException {
        fileOutputStream = new FileOutputStream(file);

        ByteBuf byteBuf = Unpooled.buffer();
        fileOutputStream.write(ByteBufUtil.getBytes( PcapHeaders.generateGlobalHeader(byteBuf)));
    }

    public void writePacket(ByteBuf packet) throws IOException {
        long difference = System.nanoTime() - myStartTime;

        ByteBuf byteBuf = PcapHeaders.generatePacketHeader(
                Unpooled.buffer(),
                (int) (difference / 1000000000),
                (int) difference / 1000000,
                packet.readableBytes(),
                packet.readableBytes()
        );

        fileOutputStream.write(ByteBufUtil.getBytes(byteBuf));
        fileOutputStream.write(ByteBufUtil.getBytes(packet));
    }

    @Override
    public void close() throws IOException {
        this.fileOutputStream.close();
    }
}
