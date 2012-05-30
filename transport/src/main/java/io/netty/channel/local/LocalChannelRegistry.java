package io.netty.channel.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class LocalChannelRegistry {

    private static final ConcurrentMap<LocalAddress, Channel> boundChannels =
            new ConcurrentHashMap<LocalAddress, Channel>();

    static LocalAddress register(
            Channel channel, LocalAddress oldLocalAddress, SocketAddress localAddress) {
        if (oldLocalAddress != null) {
            throw new ChannelException("already bound");
        }
        if (!(localAddress instanceof LocalAddress)) {
            throw new ChannelException(
                    "unsupported address type: " + localAddress.getClass().getSimpleName());
        }

        LocalAddress addr = (LocalAddress) localAddress;
        if (LocalAddress.ANY.equals(addr)) {
            addr = new LocalAddress(channel);
        }

        Channel boundChannel = boundChannels.putIfAbsent(addr, channel);
        if (boundChannel != null) {
            throw new ChannelException("address already in use by: " + boundChannel);
        }
        return addr;
    }

    static Channel get(SocketAddress localAddress) {
        return boundChannels.get(localAddress);
    }

    static void unregister(LocalAddress localAddress) {
        boundChannels.remove(localAddress);
    }

    private LocalChannelRegistry() {
        // Unused
    }
}
