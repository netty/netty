package io.netty.channel.socket.nio2;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.util.Map;
import java.util.Set;

public class AsyncNetworkChannel implements NetworkChannel {

    private Map<SocketOption<?>, Object> options;
    
    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException();

    }

    @Override
    public NetworkChannel bind(SocketAddress local) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized <T> T getOption(SocketOption<T> name) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public synchronized <T> NetworkChannel setOption(SocketOption<T> name, T value) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        throw new UnsupportedOperationException();
    }

}
