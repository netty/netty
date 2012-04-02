/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import static io.netty.channel.Channels.fireChannelOpen;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.Channels;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provides an NIO based {@link io.netty.channel.socket.DatagramChannel}.
 */
public final class NioDatagramChannel extends AbstractNioChannel implements io.netty.channel.socket.DatagramChannel {

    /**
     * The {@link DatagramChannelConfig}.
     */
    private final NioDatagramChannelConfig config;
    private Map<InetAddress, List<MembershipKey>> memberships;
   
    static NioDatagramChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, NioDatagramWorker worker) {
        NioDatagramChannel instance =
                new NioDatagramChannel(factory, pipeline, sink, worker);
        fireChannelOpen(instance);
        return instance;
    }

    private NioDatagramChannel(final ChannelFactory factory,
            final ChannelPipeline pipeline, final ChannelSink sink,
            final NioDatagramWorker worker) {
        super(null, factory, pipeline, sink, worker, new NioDatagramJdkChannel(openNonBlockingChannel()));
        config = new DefaultNioDatagramChannelConfig(getJdkChannel().getChannel());
    }

    private static DatagramChannel openNonBlockingChannel() {
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            return channel;
        } catch (final IOException e) {
            throw new ChannelException("Failed to open a DatagramChannel.", e);
        }
    }


    @Override
    protected NioDatagramJdkChannel getJdkChannel() {
        return (NioDatagramJdkChannel) super.getJdkChannel();
    }

    @Override
    public NioDatagramWorker getWorker() {
        return (NioDatagramWorker) super.getWorker();
    }

    @Override
    public boolean isBound() {
        return isOpen() && getJdkChannel().isSocketBound();
    }

    @Override
    public boolean isConnected() {
        return getJdkChannel().isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    public NioDatagramChannelConfig getConfig() {
        return config;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
       try {
            return joinGroup(multicastAddress, NetworkInterface.getByInetAddress(getLocalAddress().getAddress()), null);
        } catch (SocketException e) {
            return Channels.failedFuture(this, e);
        }
    }

    @Override
    public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null);
    }

    /**
     * Joins the specified multicast group at the specified interface using the specified source.
     */
    public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            if (multicastAddress == null) {
                throw new NullPointerException("multicastAddress");
            }
            
            if (networkInterface == null) {
                throw new NullPointerException("networkInterface");
            }
            
            try {
                MembershipKey key;
                if (source == null) {
                    key = getJdkChannel().getChannel().join(multicastAddress, networkInterface);
                } else {
                    key = getJdkChannel().getChannel().join(multicastAddress, networkInterface, source);
                }

                synchronized (this) {
                    if (memberships == null) {
                        memberships = new HashMap<InetAddress, List<MembershipKey>>();
                       
                    } 
                    List<MembershipKey> keys = memberships.get(multicastAddress);
                    if (keys == null) {
                        keys = new ArrayList<MembershipKey>();
                        memberships.put(multicastAddress, keys);
                    }
                    keys.add(key);
                }
            } catch (Throwable e) {
                return Channels.failedFuture(this, e);
            }
        }
        return Channels.succeededFuture(this);
    }
    
    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        try {
            return leaveGroup(multicastAddress, NetworkInterface.getByInetAddress(getLocalAddress().getAddress()), null);
        } catch (SocketException e) {
            return Channels.failedFuture(this, e);
        }
        
    }

    @Override
    public ChannelFuture leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null);
    }

    /**
     * Leave the specified multicast group at the specified interface using the specified source.
     */
    public ChannelFuture leaveGroup(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress source) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            if (multicastAddress == null) {
                throw new NullPointerException("multicastAddress");
            }
            
            if (networkInterface == null) {
                throw new NullPointerException("networkInterface");
            }
            
            synchronized (this) {
                if (memberships != null) {
                    List<MembershipKey> keys = memberships.get(multicastAddress);
                    if (keys != null) {
                        Iterator<MembershipKey> keyIt = keys.iterator();
                        
                        while (keyIt.hasNext()) {
                            MembershipKey key = keyIt.next();
                            if (networkInterface.equals(key.networkInterface())) {
                               if (source == null && key.sourceAddress() == null || (source != null && source.equals(key.sourceAddress()))) {
                                   key.drop();
                                   keyIt.remove();
                               }
                               
                            }
                        }
                        if (keys.isEmpty()) {
                            memberships.remove(multicastAddress);
                        }
                    }
                }
            }
            return Channels.succeededFuture(this);
        }
    }
    
    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     * 
     */
    public ChannelFuture block(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress sourceToBlock) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            if (multicastAddress == null) {
                throw new NullPointerException("multicastAddress");
            }
            if (sourceToBlock == null) {
                throw new NullPointerException("sourceToBlock");
            }
            
            if (networkInterface == null) {
                throw new NullPointerException("networkInterface");
            }
            synchronized (this) {
                if (memberships != null) {
                    List<MembershipKey> keys = memberships.get(multicastAddress);
                    for (MembershipKey key: keys) {
                        if (networkInterface.equals(key.networkInterface())) {
                            try {
                                key.block(sourceToBlock);
                            } catch (IOException e) {
                                return Channels.failedFuture(this, e);
                            }
                        }
                    }
                }
            }
            return Channels.succeededFuture(this);
            
            
        }
    }
    
    /**
     * Block the given sourceToBlock address for the given multicastAddress
     * 
     */
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        try {
            block(multicastAddress, NetworkInterface.getByInetAddress(getLocalAddress().getAddress()), sourceToBlock);
        } catch (SocketException e) {
            return Channels.failedFuture(this, e);
        }
        return Channels.succeededFuture(this);

    }
    
    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }

    }
}
