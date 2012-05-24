package io.netty.channel.socket;

import io.netty.buffer.ChannelBuffer;

import java.net.InetSocketAddress;

public class DatagramPacket {

    private final ChannelBuffer data;
    private final InetSocketAddress remoteAddress;

    public DatagramPacket(ChannelBuffer data, InetSocketAddress remoteAddress) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }

        this.data = data;
        this.remoteAddress = remoteAddress;
    }

    public ChannelBuffer data() {
        return data;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public String toString() {
        return "datagram(" + data.readableBytes() + "B, " + remoteAddress + ')';
    }
}
