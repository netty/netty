package io.netty.channel.group;

import java.util.Collection;

import io.netty.channel.Channel;


public interface MappedChannelGroup<K> extends ChannelGroup {

	Channel getChannel(K key);
	
	Collection<Channel> getAllChannel(K... keys);
	
	Collection<Channel> getAllChannel(Collection<K> keys);
	
	Channel removeChannel(K key);
	
	Collection<Channel> removeAllChannel(K... keys);
	
	Collection<Channel> removeAllChannel(Collection<K> keys);
}
