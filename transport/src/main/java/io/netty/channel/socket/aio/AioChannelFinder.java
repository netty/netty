package io.netty.channel.socket.aio;

interface AioChannelFinder {
    AbstractAioChannel findChannel(Runnable command) throws Exception;
}