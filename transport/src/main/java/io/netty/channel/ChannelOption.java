package io.netty.channel;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChannelOption<T> implements Comparable<ChannelOption<T>> {

    private static final ConcurrentMap<String, Boolean> names = new ConcurrentHashMap<String, Boolean>();

    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS =
            new ChannelOption<Integer>("CONNECT_TIMEOUT_MILLIS", Integer.class);
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT =
            new ChannelOption<Integer>("WRITE_SPIN_COUNT", Integer.class);

    public static final ChannelOption<Boolean> SO_BROADCAST =
            new ChannelOption<Boolean>("SO_BROADCAST", Boolean.class);
    public static final ChannelOption<Boolean> SO_KEEPALIVE =
            new ChannelOption<Boolean>("SO_KEEPALIVE", Boolean.class);
    public static final ChannelOption<Integer> SO_SNDBUF =
            new ChannelOption<Integer>("SO_SNDBUF", Integer.class);
    public static final ChannelOption<Integer> SO_RCVBUF =
            new ChannelOption<Integer>("SO_RCVBUF", Integer.class);
    public static final ChannelOption<Boolean> SO_REUSEADDR =
            new ChannelOption<Boolean>("SO_REUSEADDR", Boolean.class);
    public static final ChannelOption<Integer> SO_LINGER =
            new ChannelOption<Integer>("SO_LINGER", Integer.class);
    public static final ChannelOption<Integer> SO_BACKLOG =
            new ChannelOption<Integer>("SO_BACKLOG", Integer.class);

    public static final ChannelOption<Integer> IP_TOS =
            new ChannelOption<Integer>("IP_TOS", Integer.class);
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR =
            new ChannelOption<InetAddress>("IP_MULTICAST_ADDR", InetAddress.class);
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF =
            new ChannelOption<NetworkInterface>("IP_MULTICAST_IF", NetworkInterface.class);
    public static final ChannelOption<Integer> IP_MULTICAST_TTL =
            new ChannelOption<Integer>("IP_MULTICAST_TTL", Integer.class);
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED =
            new ChannelOption<Boolean>("IP_MULTICAST_LOOP_DISABLED", Boolean.class);

    public static final ChannelOption<Boolean> TCP_NODELAY =
            new ChannelOption<Boolean>("TCP_NODELAY", Boolean.class);

    public static final ChannelOption<Boolean> SCTP_DISABLE_FRAGMENTS =
            new ChannelOption<Boolean>("SCTP_DISABLE_FRAGMENTS", Boolean.class);
    public static final ChannelOption<Boolean> SCTP_EXPLICIT_COMPLETE =
            new ChannelOption<Boolean>("SCTP_EXPLICIT_COMPLETE", Boolean.class);
    public static final ChannelOption<Integer> SCTP_FRAGMENT_INTERLEAVE =
            new ChannelOption<Integer>("SCTP_FRAGMENT_INTERLEAVE", Integer.class);
    @SuppressWarnings("unchecked")
    public static final ChannelOption<List<Integer>> SCTP_INIT_MAXSTREAMS =
            new ChannelOption<List<Integer>>("SCTP_INIT_MAXSTREAMS", (Class<List<Integer>>)(Class<?>) List.class) {
        @Override
        public void validate(List<Integer> value) {
            super.validate(value);
            if (value.size() != 2) {
                throw new IllegalArgumentException("value must be a List of 2 Integers: " + value);
            }
            if (value.get(0) == null) {
                throw new NullPointerException("value[0]");
            }
            if (value.get(1) == null) {
                throw new NullPointerException("value[1]");
            }
        }
    };
    public static final ChannelOption<Boolean> SCTP_NODELAY =
            new ChannelOption<Boolean>("SCTP_NODELAY", Boolean.class);
    public static final ChannelOption<SocketAddress> SCTP_PRIMARY_ADDR =
            new ChannelOption<SocketAddress>("SCTP_PRIMARY_ADDR", SocketAddress.class);
    public static final ChannelOption<SocketAddress> SCTP_SET_PEER_PRIMARY_ADDR =
            new ChannelOption<SocketAddress>("SCTP_SET_PEER_PRIMARY_ADDR", SocketAddress.class);

    private final String name;
    private final Class<T> valueType;
    private final String strVal;

    public ChannelOption(String name, Class<T> valueType) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (valueType == null) {
            throw new NullPointerException("valueType");
        }

        if (names.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("option name already in use: " + name);
        }

        this.name = name;
        this.valueType = valueType;
        strVal = name + '[' + valueType.getSimpleName() + ']';
    }

    public String name() {
        return name;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public void validate(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }

    @Override
    public int compareTo(ChannelOption<T> o) {
        return name().compareTo(o.name());
    }

    @Override
    public String toString() {
        return strVal;
    }
}
