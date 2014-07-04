package io.netty.channel.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import io.netty.channel.EventLoop;

public interface INioEventLoop extends EventLoop {

	Selector selector();

	int selectNow() throws IOException;

	void cancel(SelectionKey selectionKey);

}