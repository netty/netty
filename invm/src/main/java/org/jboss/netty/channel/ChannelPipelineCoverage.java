/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
 * <h3>{@code ChannelPipelineCoverage("one")}</h3>
 *
 * {@code "one"} means you must create a new instance of the annotated handler
 * type for each new channel.  It means the member variables of the handler
 * instance can not be shared at all, and violating this contract will lead
 * the handler to a race condition.  The following code shows an example of a
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
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
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
