package io.netty.channel.group;

import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultMappedChannelGroup<K> extends DefaultChannelGroup implements MappedChannelGroup<K> {

    private static final AtomicInteger nextId = new AtomicInteger();
    private final ConcurrentMap<K, Channel> serverChannels = PlatformDependent.newConcurrentHashMap();
    private final ConcurrentMap<K, Channel> nonServerChannels = PlatformDependent.newConcurrentHashMap();
    private final AttributeKey<K> mappedAttributeKey;
    
    public DefaultMappedChannelGroup(EventExecutor executor, AttributeKey<K> mappedAttributeKey) {
        this("mapped-group-0x" + Integer.toHexString(nextId.incrementAndGet()), executor, mappedAttributeKey);
    }

    public DefaultMappedChannelGroup(String name, EventExecutor executor, AttributeKey<K> mappedAttributeKey) {
        super(name, executor);
        this.mappedAttributeKey = mappedAttributeKey;
    }
    
    @Override
    public boolean isEmpty() {
        return nonServerChannels.isEmpty() && serverChannels.isEmpty();
    }

    @Override
    public int size() {
        return nonServerChannels.size() + serverChannels.size();
    }
    
    @Override
    public boolean contains(Object o) {
        if (o instanceof Channel) {
            Channel c = (Channel) o;
            K key = c.attr(mappedAttributeKey).get();
            if (key != null) {
	            if (o instanceof ServerChannel) {
	                return serverChannels.containsKey(key);
	            } else {
	                return nonServerChannels.containsKey(key);
	            }
            } else {
            	return false;
            }
        } else {
            return false;
        }
    }
    
    @Override
    public Channel getChannel(K key) {
    	Channel channel = nonServerChannels.get(key);
    	if (channel != null) {
    		return channel;
    	}
    	return serverChannels.get(key);
    }
    
    @Override
    public Collection<Channel> getAllChannel(K... keys) {
    	return getAllChannel(Arrays.asList(keys));
    }
    
    @Override
    public Collection<Channel> getAllChannel(Collection<K> keys) {
    	Collection<Channel> channels = new ArrayList<Channel>(keys.size());
    	for (K key : keys) {
    		Channel channel = getChannel(key);
    		if (channel != null) {
    			channels.add(channel);
    		}
    	}
    	return channels;
    }
    
    @Override
    public boolean add(Channel channel) {
    	K key = channel.attr(mappedAttributeKey).get();
    	if (key == null) {
    		throw new IllegalArgumentException("MappedChannelGroup require Channel to provide a mapped attribute key");
    	}
        ConcurrentMap<K, Channel> map =
            channel instanceof ServerChannel? serverChannels : nonServerChannels;

        boolean added = map.putIfAbsent(key, channel) == null;
        if (added) {
            channel.closeFuture().addListener(remover);
        }
        return added;
    }
    
    @Override
    public boolean remove(Object o) {
        if (!(o instanceof Channel)) {
            return false;
        }
        boolean removed;
        Channel c = (Channel) o;
        K key = c.attr(mappedAttributeKey).get();
        if (key == null) {
        	return false;
        }
        if (c instanceof ServerChannel) {
            removed = serverChannels.remove(key) != null;
        } else {
            removed = nonServerChannels.remove(key) != null;
        }
        if (!removed) {
            return false;
        }

        c.closeFuture().removeListener(remover);
        return true;
    }
    
    @Override
    public Channel removeChannel(K key) {
    	Channel channel = nonServerChannels.remove(key);
    	if (channel != null) {
    		return channel;
    	}
    	return serverChannels.remove(key);
    }
    
    @Override
    public Collection<Channel> removeAllChannel(K... keys) {
    	return removeAllChannel(Arrays.asList(keys));
    }
    
    @Override
    public Collection<Channel> removeAllChannel(Collection<K> keys) {
    	Collection<Channel> channels = new ArrayList<Channel>(keys.size());
    	for (K key : keys) {
    		Channel channel = removeChannel(key);
    		if (channel != null) {
    			channels.add(channel);
    		}
    	}
    	return channels;
    }
    
    @Override
    public void clear() {
        nonServerChannels.clear();
        serverChannels.clear();
    }

    @Override
    public Iterator<Channel> iterator() {
        return new CombinedIterator<Channel>(
                serverChannels.values().iterator(),
                nonServerChannels.values().iterator());
    }
    
    @Override
    public Object[] toArray() {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels.values());
        channels.addAll(nonServerChannels.values());
        return channels.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels.values());
        channels.addAll(nonServerChannels.values());
        return channels.toArray(a);
    }

    @Override
    protected Collection<Channel> serverChannels() {
    	return this.serverChannels.values();
    }
    
    @Override
    protected Collection<Channel> nonServerChannels() {
    	return this.nonServerChannels.values();
    }
}
