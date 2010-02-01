/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ConcurrentMap;

/**
 * Specifies if the same instance of the annotated {@link ChannelHandler} type
 * can be added to more than one {@link ChannelPipeline}.
 * <p>
 * All handler types are expected to specify this annotation.  Otherwise you
 * will be warned in runtime.  Only two values are allowed for this annotation:
 * {@code "all"} and {@code "one"}.
 * <p>
 * Please note that this annotation does not prevent a handler annotated with
 * the value {@code "one"} from being added to more than one pipeline.  This
 * annotation is used for documentation purpose only.
 *
 * <h3>{@code ChannelPipelineCoverage("all")}</h3>
 *
 * {@code "all"} means you can add the same instance of the annotated handler
 * type to more than one {@link ChannelPipeline}.  It means the member
 * variables of the handler instance is shared among multiple channels and it
 * is designed to be OK to do so (or there's nothing to share.)  The following
 * code shows an example of a handler type annotated with {@code "all"} value:
 *
 * <pre>
 * public class StatelessHandler extends SimpleChannelHandler {
 *
 *     // No state properties - you are safe to add the same instance to
 *     //                       multiple pipelines.
 *
 *     public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *         // Prepend a length field to the message.
 *         ChannelBuffer body = (ChannelBuffer) e.getMessage();
 *         ChannelBuffer header = ChannelBuffers.buffer(4);
 *         header.writeInt(body.readableBytes());
 *
 *         // Create a message prepended with the header and send a new event.
 *         ChannelBuffer message = ChannelBuffers.wrappedBuffer(header, body);
 *         Channels.fireMessageReceived(ctx, message, e.getRemoteAddress());
 *     }
 *     ...
 * }
 * </pre>
 *
 * Please note that the handler annotated with {@code "all"} can even be added
 * to the same pipeline more than once:
 *
 * <pre>
 * ChannelPipeline p = ...;
 * StatelessHandler h = ...;
 * p.addLast("handler1", h);
 * p.addLast("handler2", h);
 * </pre>
 *
 * <h3>{@code ChannelPipelineCoverage("one")}</h3>
 *
 * {@code "one"} means you must create a new instance of the annotated handler
 * type for each new channel.  It means the member variables of the handler
 * instance can not be shared at all, and violating this contract will lead
 * the handler to a race condition.  A new handler instance is usually created
 * by {@link ChannelPipelineFactory}.  The following code shows an example of a
 * handler type annotated with {@code "one"} value:
 *
 * <pre>
 * public class StatefulHandler extends SimpleChannelHandler {
 *
 *     // Stateful property - adding the same instance to multiple pipelines
 *     //                     can lead to a race condition.
 *     private int messageId;
 *
 *     public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *         // Prepend a message ID and length field to the message.
 *         ChannelBuffer body = (ChannelBuffer) e.getMessage();
 *         ChannelBuffer header = ChannelBuffers.buffer(8);
 *         header.writeInt(messageId);
 *         header.writeInt(body.readableBytes());
 *
 *         // Update the stateful property.
 *         messageId ++;
 *
 *         // Create a message prepended with the header and send a new event.
 *         ChannelBuffer message = ChannelBuffers.wrappedBuffer(header, body);
 *         Channels.fireMessageReceived(ctx, message, e.getRemoteAddress());
 *     }
 *     ...
 * }
 *
 * // Create a new handler instance per channel.
 * public class MyPipelineFactory implements ChannelPipelineFactory {
 *
 *     public ChannelPipeline getPipeline() {
 *         ChannelPipeline p = Channels.pipeline();
 *         p.addLast("handler", new StatefulHandler());
 *     }
 * }
 * </pre>
 *
 * <h3>Writing a stateful handler with the {@code "all"} coverage</h3>
 *
 * Although it's recommended to write a stateful handler with the {@code "one"}
 * coverage, you might sometimes want to write it with the {@code "all"}
 * coverage for some reason.  In such a case, you need to use either
 * {@link ChannelHandlerContext#setAttachment(Object) ChannelHandlerContext.attachment}
 * property or a {@link ConcurrentMap} whose key is {@link ChannelHandlerContext}.
 * <p>
 * The following is an example that uses the context attachment:
 * <pre>
 * public class StatefulHandler extends SimpleChannelHandler {
 *
 *     public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
 *         // Initialize the message ID counter.
 *         // Please note that the attachment (the counter in this case) will be
 *         // dereferenced and marked for garbage collection automatically on
 *         // disconnection.
 *         <strong>ctx.setAttachment(Integer.valueOf(0));</strong>
 *     }
 *
 *     public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *         // Fetch the current message ID.
 *         <strong>int messageId = ((Integer) ctx.getAttachment()).intValue();</strong>
 *
 *         // Prepend a message ID and length field to the message.
 *         ChannelBuffer body = (ChannelBuffer) e.getMessage();
 *         ChannelBuffer header = ChannelBuffers.buffer(8);
 *         header.writeInt(messageId);
 *         header.writeInt(body.readableBytes());
 *
 *         // Update the stateful property.
 *         <strong>ctx.setAttachment(Integer.valueOf(messageId + 1));</strong>
 *
 *         // Create a message prepended with the header and send a new event.
 *         ChannelBuffer message = ChannelBuffers.wrappedBuffer(header, body);
 *         Channels.fireMessageReceived(ctx, message, e.getRemoteAddress());
 *     }
 *     ...
 * }
 * </pre>
 *
 * and here's another example that uses a map:
 * <pre>
 * public class StatefulHandler extends SimpleChannelHandler {
 *
 *     <strong>private final ConcurrentMap&lt;ChannelHandlerContext, Integer&gt; messageIds =
 *             new ConcurrentHashMap&lt;ChannelHandlerContext, Integer&gt;();</strong>
 *
 *     public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
 *         // Initialize the message ID counter.
 *         <strong>messageIds.put(ctx, Integer.valueOf(0));</strong>
 *     }
 *
 *     public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
 *         // Remove the message ID counter from the map.
 *         // Please note that the context attachment does not need this step.
 *         <strong>messageIds.remove(ctx);</strong>
 *     }
 *
 *     public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *         // Fetch the current message ID.
 *         <strong>int messageId = messageIds.get(ctx).intValue();</strong>
 *
 *         // Prepend a message ID and length field to the message.
 *         ChannelBuffer body = (ChannelBuffer) e.getMessage();
 *         ChannelBuffer header = ChannelBuffers.buffer(8);
 *         header.writeInt(messageId);
 *         header.writeInt(body.readableBytes());
 *
 *         // Update the stateful property.
 *         <strong>messageIds.put(ctx, Integer.valueOf(messageId + 1));</strong>
 *
 *         // Create a message prepended with the header and send a new event.
 *         ChannelBuffer message = ChannelBuffers.wrappedBuffer(header, body);
 *         Channels.fireMessageReceived(ctx, message, e.getRemoteAddress());
 *     }
 *     ...
 * }
 * </pre>
 *
 * Please note that the examples above in this section assume that the handlers
 * are added before the {@code channelOpen} event and removed after the
 * {@code channelClosed} event.  The initialization and removal of the message
 * ID property could have been more complicated otherwise.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Deprecated
public @interface ChannelPipelineCoverage {

    /**
     * {@code "all"}
     */
    public static final String ALL = "all";

    /**
     * {@code "one"}
     */
    public static final String ONE = "one";

    /**
     * The value of this annotation
     */
    String value();
}
