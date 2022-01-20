package io.netty.channel.nio;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategyFactory;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class NioEventLoopGroupBuilder {
	private int nThreads = 0;
	private Executor executor = (Executor) null;
	private ThreadFactory threadFactory;
	private SelectorProvider selectorProvider = SelectorProvider.provider();
	private SelectStrategyFactory selectStrategyFactory = DefaultSelectStrategyFactory.INSTANCE;
	private EventExecutorChooserFactory chooserFactory;
	private RejectedExecutionHandler rejectedExecutionHandler;
	private EventLoopTaskQueueFactory taskQueueFactory;
	private EventLoopTaskQueueFactory tailTaskQueueFactory;

	public NioEventLoopGroupBuilder setnThreads(int nThreads) {
		this.nThreads = nThreads;
		return this;
	}

	public NioEventLoopGroupBuilder setExecutor(Executor executor) {
		this.executor = executor;
		return this;
	}

	public NioEventLoopGroupBuilder setThreadFactory(ThreadFactory threadFactory) {
		this.threadFactory = threadFactory;
		return this;
	}

	public NioEventLoopGroupBuilder setSelectorProvider(SelectorProvider selectorProvider) {
		this.selectorProvider = selectorProvider;
		return this;
	}

	public NioEventLoopGroupBuilder setSelectStrategyFactory(SelectStrategyFactory selectStrategyFactory) {
		this.selectStrategyFactory = selectStrategyFactory;
		return this;
	}

	public NioEventLoopGroupBuilder setChooserFactory(EventExecutorChooserFactory chooserFactory) {
		this.chooserFactory = chooserFactory;
		return this;
	}

	public NioEventLoopGroupBuilder setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
		this.rejectedExecutionHandler = rejectedExecutionHandler;
		return this;
	}

	public NioEventLoopGroupBuilder setTaskQueueFactory(EventLoopTaskQueueFactory taskQueueFactory) {
		this.taskQueueFactory = taskQueueFactory;
		return this;
	}

	public NioEventLoopGroupBuilder setTailTaskQueueFactory(EventLoopTaskQueueFactory tailTaskQueueFactory) {
		this.tailTaskQueueFactory = tailTaskQueueFactory;
		return this;
	}

	public NioEventLoopGroup createNioEventLoopGroup() {
		return new NioEventLoopGroup(nThreads);
	}
}