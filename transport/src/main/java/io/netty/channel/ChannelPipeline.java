/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.Buf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.util.concurrent.EventExecutorGroup;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts
 * inbound and outbount operations of a {@link Channel}.  {@link ChannelPipeline}
 * implements an advanced form of the
 * <a href="http://java.sun.com/blueprints/corej2eepatterns/Patterns/InterceptingFilter.html">Intercepting
 * Filter</a> pattern to give a user full control over how an event is handled
 * and how the {@link ChannelHandler}s in the pipeline interact with each other.
 *
 * <h3>Creation of a pipeline</h3>
 *
 * For each new channel, a new pipeline iscreated and attached to the
 * channel.  Once attached, the coupling between the channel and the pipeline
 * is permanent; the channel cannot attach another pipeline to it nor detach
 * the current pipeline from it. All of this is handled for you and you not need
 * to take care of this.
 * <p>
 *
 *
 * <h3>How an event flows in a pipeline</h3>
 *
 * The following diagram describes how I/O is processed by
 * {@link ChannelHandler}s in a {@link ChannelPipeline} typically.
 * A I/O-operation can be handled by either a {@link ChannelInboundHandler}
 * or a {@link ChannelOutboundHandler} and be forwarded to the closest
 * handler by calling either one of the methods defined in the
 * {@link ChannelInboundInvoker} interface for inbound I/O or by one
 * of the methods defined in the {@link ChannelOutboundInvoker} interface
 * for outbound I/O. {@link ChannelPipeline} extends both of them.
 *
 * <pre>
 *                                                  I/O Request
 *                                             via {@link Channel} or
 *                                         {@link ChannelHandlerContext}
 *                                                       |
 *  +----------------------------------------------------+-----------------+
 *  |                           ChannelPipeline          |                 |
 *  |                                                   \|/                |
 *  |    +----------------------+            +-----------+------------+    |
 *  |    | Inbound Handler  N   |            | Outbound Handler  1    |    |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |              /|\                                   |                 |
 *  |               |                                   \|/                |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |    | Inbound Handler N-1  |            |   Outbound Handler  2  |    |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |              /|\                                   .                 |
 *  |               .                                    .                 |
 *  | [{@link ChannelInboundInvoker}]   [{@link ChannelOutboundInvoker}()] |
 *  |        [ method call]                    [method call]               |
 *  |               .                                    .                 |
 *  |               .                                   \|/                |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |    | Inbound Handler  2   |            | Outbound Handler M-1   |    |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |              /|\                                   |                 |
 *  |               |                                   \|/                |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |    | Inbound Handler  1   |            | Outbound Handler  M    |    |
 *  |    +----------+-----------+            +-----------+------------+    |
 *  |              /|\                                   |                 |
 *  +---------------+------------------------------------+-----------------+
 *                  |                                   \|/
 *  +---------------+------------------------------------+-----------------+
 *  |               |                                    |                 |
 *  |       [ Socket.read() ]                     [ Socket.write() ]       |
 *  |                                                                      |
 *  |  Netty Internal I/O Threads (Transport Implementation)               |
 *  +----------------------------------------------------------------------+
 * </pre>
 * An inbound event is handled by the inbound handlers in the bottom-up
 * direction as shown on the left side of the diagram.  An inbound handler
 * usually handles the inbound data generated by the I/O thread on the bottom
 * of the diagram.  The inbound data is often read from a remote peer via the
 * actual input operation such as {@link InputStream#read(byte[])}.
 * If an inbound event goes beyond the top inbound handler, it is discarded
 * silently.
 * <p>
 * A outbound event is handled by the outbound handler in the top-down
 * direction as shown on the right side of the diagram.  A outbound handler
 * usually generates or transforms the outbound traffic such as write requests.
 * If a outbound event goes beyond the bottom outbound handler, it is
 * handled by an I/O thread associated with the {@link Channel}. The I/O thread
 * often performs the actual output operation such as {@link OutputStream#write(byte[])}.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means
 * it is an inbound handler.  The class whose name starts with
 * {@code Outbound} means it is a outbound handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3,
 * 4, 5 when an event goes inbound.  When an event goes outbound, the order
 * is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelInboundHandler}, and therefore the
 *     actual evaluation order of an inbound event will be: 1, 2, and 5.</li>
 * <li>1, 2, and 5 don't implement {@link ChannelOutboundHandler}, and
 *     therefore the actual evaluation order of a outbound event will be:
 *     4 and 3.</li>
 * <li>If 5 implements both
 *     {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}, the
 *     evaluation order of an inbound and a outbound event could be 125 and
 *     543 respectively.</li>
 * </ul>
 *
 * <h3>Building a pipeline</h3>
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a
 * pipeline to receive I/O events (e.g. read) and to request I/O operations
 * (e.g. write and close).  For example, a typical server will have the following
 * handlers in each channel's pipeline, but your mileage may vary depending on
 * the complexity and characteristics of the protocol and business logic:
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ByteBuf})
 *                        into a Java object.</li>
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 * <li><tt>ExecutionHandler</tt> - applies a thread model.</li>
 * <li>Business Logic Handler - performs the actual business logic
 *                              (e.g. database access).</li>
 * </ol>
 *
 * and it could be represented as shown in the following example:
 *
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 * pipeline.addLast("executor", new ExecutionHandler(...));
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a
 * {@link ChannelPipeline} is thread safe.  For example, you can insert an
 * encryption handler when sensitive information is about to be exchanged,
 * and remove it after the exchange.
 */
public interface ChannelPipeline extends ChannelInboundInvoker, ChannelOutboundInvoker,
        Iterable<Entry<String, ChannelHandler>> {

    /**
     * Return the bound {@link MessageBuf} of the first {@link ChannelInboundMessageHandler} in the
     * {@link ChannelPipeline}. If no {@link ChannelInboundMessageHandler} exists in the {@link ChannelPipeline}
     * it will throw a {@link UnsupportedOperationException}.
     * <p/>
     * This method can only be called from within the event-loop, otherwise it will throw an
     * {@link IllegalStateException}.
     */
    <T> MessageBuf<T> inboundMessageBuffer();

    /**
     * Return the bound {@link ByteBuf} of the first {@link ChannelInboundByteHandler} in the
     * {@link ChannelPipeline}. If no {@link ChannelInboundByteHandler} exists in the {@link ChannelPipeline}
     * it will throw a {@link UnsupportedOperationException}.
     * <p/>
     * This method can only be called from within the event-loop, otherwise it will throw an
     * {@link IllegalStateException}.
     */
    ByteBuf inboundByteBuffer();

    /**
     * Return the bound {@link MessageBuf} of the first {@link ChannelOutboundMessageHandler} in the
     * {@link ChannelPipeline}. If no {@link ChannelOutboundMessageHandler} exists in the {@link ChannelPipeline}
     * it will throw a {@link UnsupportedOperationException}.
     * <p/>
     * This method can only be called from within the event-loop, otherwise it will throw an
     * {@link IllegalStateException}.
     */
    <T> MessageBuf<T> outboundMessageBuffer();

    /**
     * Return the bound {@link ByteBuf} of the first {@link ChannelOutboundByteHandler} in the
     * {@link ChannelPipeline}. If no {@link ChannelOutboundByteHandler} exists in the {@link ChannelPipeline}
     * it will throw a {@link UnsupportedOperationException}.
     * <p/>
     * This method can only be called from within the event-loop, otherwise it will throw an
     * {@link IllegalStateException}.
     */
    ByteBuf outboundByteBuffer();

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * Inserts a {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Inserts a {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * Inserts a {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline
     * and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param  handler          the {@link ChannelHandler} to remove
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this
     * pipeline and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param  name             the name under which the {@link ChannelHandler} was stored.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this
     * pipeline and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param <T>           the type of the handler
     * @param handlerType   the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * All the remaining content in the {@link Buf) (if any) of the {@link ChannelHandler}
     * will be discarded.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * All the remaining content in the {@link Buf) (if any) of the {@link ChannelHandler}
     * will be discarded.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in
     * this pipeline and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param  oldHandler    the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return itself

     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new
     * handler in this pipeline and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param  oldName       the name of the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new
     * handler in this pipeline and transfer the content of its {@link Buf} to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * @param  oldHandlerType   the type of the handler to be removed
     * @param  newName          the name under which the replacement should be added
     * @param  newHandler       the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the context of the first {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the context of the last {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireInboundBufferUpdated();

    @Override
    ChannelPipeline fireChannelReadSuspended();
}
