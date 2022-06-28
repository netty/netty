/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel;

import io.netty5.channel.ChannelHandlerMask.Skip;
import io.netty5.util.Attribute;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.Future;

import java.net.SocketAddress;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * <h3>State management</h3>
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 * <pre>
 * // Create a new handler instance per channel.
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use {@link AttributeKey}s which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public boolean isSharable() {
 *         return true;
 *     }
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is attached to the {@link ChannelHandlerContext}, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>The {@link #isSharable()} method</h4>
 * <p>
 * In the example above which used an {@link AttributeKey},
 * you might have noticed the {@link #isSharable()} method is override to return {@code true}.
 * <p>
 * If the {@link ChannelHandler#isSharable()} is returning{@code true},
 * it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this method is not implemented and return {@code true}, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     */
    default void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     */
    default void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Return {@code true} if the implementation is sharable and so can be added
     * to different {@link ChannelPipeline}s. By default, this returns {@code false}.
     * If this method returns {@code false}, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     */
    default boolean isSharable() {
        return false;
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered with its {@link EventLoop}
     */
    @Skip
    default void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was unregistered from its {@link EventLoop}
     */
    @Skip
    default void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} is now active
     */
    @Skip
    default void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     */
    @Skip
    default void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was shutdown in one direction.
     * This might either be because the remote peer did cause a shutdown of one direction or the shutdown was requested
     * explicit by us and was executed.
     *
     * @param ctx       the {@link ChannelHandlerContext} for which we notify about the completed shutdown.
     * @param direction the {@link ChannelShutdownDirection} of the completed shutdown.
     */
    @Skip
    default void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) throws Exception {
        ctx.fireChannelShutdown(direction);
    }

    /**
     * Invoked when the current {@link Channel} has read a message from the peer.
     */
    @Skip
    default void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    /**
     * Invoked when the last message read by the current read operation has been consumed by
     * {@link #channelRead(ChannelHandlerContext, Object)}.  If {@link ChannelOption#AUTO_READ} is off, no further
     * attempt to read an inbound data from the current {@link Channel} will be made until
     * {@link ChannelHandlerContext#read()} is called.
     */
    @Skip
    default void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    /**
     * Gets called if a custom inbound event happened.
     */
    @Skip
    default void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireChannelInboundEvent(evt);
    }

    /**
     * Gets called once the writable state of a {@link Channel} changed. You can check the state with
     * {@link Channel#isWritable()}.
     */
    @Skip
    default void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    /**
     * Gets called if a {@link Throwable} was thrown when handling inbound events.
     */
    @Skip
    default void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireChannelExceptionCaught(cause);
    }

    /**
     * Called once a bind operation is made.
     *
     * @param ctx           the {@link ChannelHandlerContext} for which the bind operation is made
     * @param localAddress  the {@link SocketAddress} to which it should bound
     * @return              the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
        return ctx.bind(localAddress);
    }

    /**
     * Called once a connect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the connect operation is made
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param localAddress      the {@link SocketAddress} which is used as source on connect
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress) {
        return ctx.connect(remoteAddress, localAddress);
    }

    /**
     * Called once a disconnect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the disconnect operation is made
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> disconnect(ChannelHandlerContext ctx) {
        return ctx.disconnect();
    }

    /**
     * Called once a close operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> close(ChannelHandlerContext ctx) {
        return ctx.close();
    }

    /**
     * Called once a shutdown operation was requested and should be executed.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the shutdown operation is made
     * @param direction         the {@link ChannelShutdownDirection} that is used.
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> shutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) {
        return ctx.shutdown(direction);
    }

    /**
     * Called once a register operation is made to register for IO on the {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the register operation is made
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> register(ChannelHandlerContext ctx) {
        return ctx.register();
    }

    /**
     * Called once a deregister operation is made from the current registered {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the deregister operation is made
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> deregister(ChannelHandlerContext ctx) {
        return ctx.deregister();
    }

    /**
     * Intercepts {@link ChannelHandlerContext#read()}.
     */
    @Skip
    default void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    /**
     * Called once a write operation is made. The write operation will write the messages through the
     * {@link ChannelPipeline}. Those are then ready to be flushed to the actual {@link Channel} once
     * {@link Channel#flush()} is called.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg               the message to write
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        return ctx.write(msg);
    }

    /**
     * Called once a flush operation is made. The flush operation will try to flush out all previous written messages
     * that are pending.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the flush operation is made
     */
    @Skip
    default void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * Called once a custom defined outbound event was sent. This operation will pass the event through the
     * {@link ChannelPipeline} in the outbound direction.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the operation is made.
     * @param event             the event.
     * @return                  the {@link Future} which will be notified once the operation completes.
     */
    @Skip
    default Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
        return ctx.sendOutboundEvent(event);
    }
}
