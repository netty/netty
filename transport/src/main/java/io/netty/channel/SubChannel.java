package io.netty.channel;

import java.net.SocketAddress;
import java.util.concurrent.RejectedExecutionException;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator.Handle;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class SubChannel implements Channel {

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(SubChannel.class);

	private final class SubChannelId implements ChannelId {

		private static final long serialVersionUID = 1L;

		@Override
		public int compareTo(ChannelId o) {
			return parent().id().compareTo(o);
		}

		@Override
		public String asShortText() {
			return parent().id().asShortText();
		}

		@Override
		public String asLongText() {
			return parent().id().asLongText();
		}

	}

	private final SubChannelUnsafe unsafe = new SubChannelUnsafe();
	private final ChannelId channelId;
	private final ChannelPipeline pipeline;

	protected final ChannelHandlerContext ctx;

	private volatile boolean registered;

	public SubChannel(final ChannelHandlerContext ctx) {
		this.ctx = ctx;
		pipeline = new DefaultChannelPipeline(this) {
			@Override
			protected void incrementPendingOutboundBytes(long size) {
				// Do nothing for now
			}

			@Override
			protected void decrementPendingOutboundBytes(long size) {
				// Do nothing for now
			}

			@Override
			protected void onUnhandledInboundException(Throwable cause) {
				ctx.fireExceptionCaught(cause);
			}

			@Override
			protected void onUnhandledInboundChannelActive() {
				ctx.fireChannelActive();
			}

			@Override
			protected void onUnhandledInboundChannelInactive() {
				ctx.fireChannelInactive();
			}

			@Override
			protected void onUnhandledInboundMessage(Object msg) {
				ctx.fireChannelRead(msg);
			}

			@Override
			protected void onUnhandledInboundChannelReadComplete() {
				ctx.fireChannelReadComplete();
			}

			@Override
			protected void onUnhandledInboundUserEventTriggered(Object evt) {
				ctx.fireUserEventTriggered(evt);
			}

			@Override
			protected void onUnhandledChannelWritabilityChanged() {
				ctx.fireChannelWritabilityChanged();
			}
		};
		channelId = new SubChannelId();
	}

	@Override
	public ChannelMetadata metadata() {
		return parent().metadata();
	}

	@Override
	public ChannelConfig config() {
		return parent().config();
	}

	@Override
	public boolean isOpen() {
		return parent().isOpen();
	}

	@Override
	public boolean isActive() {
		return parent().isActive();
	}

	@Override
	public boolean isWritable() {
		return parent().isWritable();
	}

	@Override
	public ChannelId id() {
		return channelId;
	}

	@Override
	public EventLoop eventLoop() {
		return parent().eventLoop();
	}

	@Override
	public Channel parent() {
		return ctx.channel();
	}

	@Override
	public boolean isRegistered() {
		return registered;
	}

	@Override
	public SocketAddress localAddress() {
		return parent().localAddress();
	}

	@Override
	public SocketAddress remoteAddress() {
		return parent().remoteAddress();
	}

	@Override
	public ChannelFuture closeFuture() {
		return parent().closeFuture();
	}

	@Override
	public long bytesBeforeUnwritable() {
		return parent().bytesBeforeUnwritable();
	}

	@Override
	public long bytesBeforeWritable() {
		return parent().bytesBeforeWritable();
	}

	@Override
	public Unsafe unsafe() {
		return unsafe;
	}

	@Override
	public ChannelPipeline pipeline() {
		return pipeline;
	}

	@Override
	public ByteBufAllocator alloc() {
		return config().getAllocator();
	}

	@Override
	public Channel read() {
		pipeline().read();
		return this;
	}

	@Override
	public Channel flush() {
		pipeline().flush();
		return this;
	}

	@Override
	public ChannelFuture bind(SocketAddress localAddress) {
		return pipeline().bind(localAddress);
	}

	@Override
	public ChannelFuture connect(SocketAddress remoteAddress) {
		return pipeline().connect(remoteAddress);
	}

	@Override
	public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
		return pipeline().connect(remoteAddress, localAddress);
	}

	@Override
	public ChannelFuture disconnect() {
		return pipeline().disconnect();
	}

	@Override
	public ChannelFuture close() {
		return pipeline().close();
	}

	@Override
	public ChannelFuture deregister() {
		return pipeline().deregister();
	}

	@Override
	public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
		return pipeline().bind(localAddress, promise);
	}

	@Override
	public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
		return pipeline().connect(remoteAddress, promise);
	}

	@Override
	public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
		return pipeline().connect(remoteAddress, localAddress, promise);
	}

	@Override
	public ChannelFuture disconnect(ChannelPromise promise) {
		return pipeline().disconnect(promise);
	}

	@Override
	public ChannelFuture close(ChannelPromise promise) {
		return pipeline().close(promise);
	}

	@Override
	public ChannelFuture deregister(ChannelPromise promise) {
		return pipeline().deregister(promise);
	}

	@Override
	public ChannelFuture write(Object msg) {
		return pipeline().write(msg);
	}

	@Override
	public ChannelFuture write(Object msg, ChannelPromise promise) {
		return pipeline().write(msg, promise);
	}

	@Override
	public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
		return pipeline().writeAndFlush(msg, promise);
	}

	@Override
	public ChannelFuture writeAndFlush(Object msg) {
		return pipeline().writeAndFlush(msg);
	}

	@Override
	public ChannelPromise newPromise() {
		return pipeline().newPromise();
	}

	@Override
	public ChannelProgressivePromise newProgressivePromise() {
		return pipeline().newProgressivePromise();
	}

	@Override
	public ChannelFuture newSucceededFuture() {
		return pipeline().newSucceededFuture();
	}

	@Override
	public ChannelFuture newFailedFuture(Throwable cause) {
		return pipeline().newFailedFuture(cause);
	}

	@Override
	public ChannelPromise voidPromise() {
		return pipeline().voidPromise();
	}

	@Override
	public int hashCode() {
		return id().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public int compareTo(Channel o) {
		if (this == o) {
			return 0;
		}

		return id().compareTo(o.id());
	}

	@Override
	public String toString() {
		return parent().toString() + "(subchannel)";
	}

	private final class SubChannelUnsafe implements Unsafe {
		private final VoidChannelPromise unsafeVoidPromise = new VoidChannelPromise(SubChannel.this, false);
		private boolean closeInitiated;

		@Override
		public void connect(final SocketAddress remoteAddress, SocketAddress localAddress,
				final ChannelPromise promise) {
			ctx.connect(remoteAddress, localAddress).addListener(new PromiseRelay(promise));
		}

		@Override
		@SuppressWarnings("deprecation")
		public Handle recvBufAllocHandle() {
			return parent().unsafe().recvBufAllocHandle();
		}

		@Override
		public SocketAddress localAddress() {
			return parent().unsafe().localAddress();
		}

		@Override
		public SocketAddress remoteAddress() {
			return parent().unsafe().remoteAddress();
		}

		@Override
		public void register(EventLoop eventLoop, ChannelPromise promise) {
			if (!promise.setUncancellable()) {
				return;
			}
			if (registered) {
				throw new UnsupportedOperationException("Re-register is not supported");
			}

			registered = true;

			promise.setSuccess();

			pipeline().fireChannelRegistered();
		}

		@Override
		public void bind(SocketAddress localAddress, ChannelPromise promise) {
			ctx.bind(localAddress).addListener(new PromiseRelay(promise));
		}

		@Override
		public void disconnect(ChannelPromise promise) {
			ctx.disconnect().addListener(new PromiseRelay(promise));
		}

		@Override
		public void close(final ChannelPromise promise) {
			if (!promise.setUncancellable()) {
				return;
			}
			if (closeInitiated) {
				if (closeFuture().isDone()) {
					// Closed already.
					promise.setSuccess();
				} else if (!(promise instanceof VoidChannelPromise)) { // Only needed if no VoidChannelPromise.
					// This means close() was called before so we just register a listener and
					// return
					closeFuture().addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) {
							promise.setSuccess();
						}
					});
				}
				return;
			}
			closeInitiated = true;

			fireChannelInactiveAndDeregister(voidPromise(), isActive());
			ctx.close().addListener(new PromiseRelay(promise));
		}

		@Override
		public void closeForcibly() {
			close(unsafe().voidPromise());
		}

		@Override
		public void deregister(ChannelPromise promise) {
			fireChannelInactiveAndDeregister(promise, false);
		}

		private void fireChannelInactiveAndDeregister(final ChannelPromise promise,
				final boolean fireChannelInactive) {
			if (!promise.setUncancellable()) {
				return;
			}

			if (!registered) {
				promise.setSuccess();
				return;
			}

			// As a user may call deregister() from within any method while doing processing
			// in the ChannelPipeline,
			// we need to ensure we do the actual deregister operation later. This is
			// necessary to preserve the
			// behavior of the AbstractChannel, which always invokes channelUnregistered and
			// channelInactive
			// events 'later' to ensure the current events in the handler are completed
			// before these events.
			//
			// See:
			// https://github.com/netty/netty/issues/4435
			invokeLater(new Runnable() {
				@Override
				public void run() {
					if (fireChannelInactive) {
						pipeline.fireChannelInactive();
					}
					// The user can fire `deregister` events multiple times but we only want to fire
					// the pipeline event if the channel was actually registered.
					if (registered) {
						registered = false;
						pipeline.fireChannelUnregistered();
					}
					safeSetSuccess(promise);
				}
			});
		}

		private void safeSetSuccess(ChannelPromise promise) {
			if (!(promise instanceof VoidChannelPromise) && !promise.trySuccess()) {
				logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
			}
		}

		private void invokeLater(Runnable task) {
			try {
				// This method is used by outbound operation implementations to trigger an
				// inbound event later.
				// They do not trigger an inbound event immediately because an outbound
				// operation might have been
				// triggered by another inbound event handler method. If fired immediately, the
				// call stack
				// will look like this for example:
				//
				// handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a
				// connection.
				// -> handlerA.ctx.close()
				// -> channel.unsafe.close()
				// -> handlerA.channelInactive() - (2) another inbound handler method called
				// while in (1) yet
				//
				// which means the execution of two inbound handler methods of the same handler
				// overlap undesirably.
				eventLoop().execute(task);
			} catch (RejectedExecutionException e) {
				logger.warn("Can't invoke task later as EventLoop rejected it", e);
			}
		}

		@Override
		public void beginRead() {
			ctx.read();
		}

		@Override
		public void write(Object msg, final ChannelPromise promise) {
			ctx.write(msg).addListener(new PromiseRelay(promise));
		}

		@Override
		public void flush() {
			ctx.flush();
		}

		@Override
		public ChannelPromise voidPromise() {
			return unsafeVoidPromise;
		}

		@Override
		public ChannelOutboundBuffer outboundBuffer() {
			// Always return null as we not use the ChannelOutboundBuffer and not even
			// support it.
			return null;
		}
	}

	@Override
	public <T> Attribute<T> attr(AttributeKey<T> key) {
		return parent().attr(key);
	}

	@Override
	public <T> boolean hasAttr(AttributeKey<T> key) {
		return parent().hasAttr(key);
	}

}