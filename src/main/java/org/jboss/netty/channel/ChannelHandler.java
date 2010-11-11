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

import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.channel.group.ChannelGroup;

/**
 * Handles or intercepts a {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide any method.  To handle a
 * {@link ChannelEvent} you need to implement its sub-interfaces.  There are
 * two sub-interfaces which handles a received event, one for upstream events
 * and the other for downstream events:
 * <ul>
 * <li>{@link ChannelUpstreamHandler} handles and intercepts an upstream {@link ChannelEvent}.</li>
 * <li>{@link ChannelDownstreamHandler} handles and intercepts a downstream {@link ChannelEvent}.</li>
 * </ul>
 *
 * You will also find more detailed explanation from the documentation of
 * each sub-interface on how an event is interpreted when it goes upstream and
 * downstream respectively.
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (attachment) which is specific to the handler.
 *
 * <h3>State management</h3>
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>loggedIn = true;</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
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
 * // See {@link Bootstrap#setPipelineFactory(ChannelPipelineFactory)}.
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>new DataServerHandler()</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>Using an attachment</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use an <em>attachment</em> which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>ctx.setAttachment(true)</b>;
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(ctx.getAttachment())</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is stored as an attachment, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>Using a {@link ChannelLocal}</h4>
 *
 * If you have a state variable which needs to be accessed either from other
 * handlers or outside handlers, you can use {@link ChannelLocal}:
 * <pre>
 * public final class DataServerState {
 *
 *     <b>public static final {@link ChannelLocal}&lt;Boolean&gt; loggedIn = new {@link ChannelLocal}&lt;Boolean&gt;() {
 *         protected Boolean initialValue(Channel channel) {
 *             return false;
 *         }
 *     }</b>
 *     ...
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         Channel ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>DataServerState.loggedIn.set(ch, true);</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *                 ctx.getChannel().write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 *
 * // Print the remote addresses of the authenticated clients:
 * {@link ChannelGroup} allClientChannels = ...;
 * for ({@link Channel} ch: allClientChannels) {
 *     if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *         System.out.println(ch.getRemoteAddress());
 *     }
 * }
 * </pre>
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * In the examples above which used an attachment or a {@link ChannelLocal},
 * you might have noticed the {@code @Sharable} annotation.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelEvent} and {@link ChannelPipeline} to find
 * out what a upstream event and a downstream event are, what fundamental
 * differences they have, and how they flow in a pipeline.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2152 $, $Date: 2010-02-17 17:22:45 +0900 (Wed, 17 Feb 2010) $
 *
 * @apiviz.landmark
 * @apiviz.exclude ^org\.jboss\.netty\.handler\..*$
 */
public interface ChannelHandler {

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     *
     * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
     * @author <a href="http://gleamynode.net/">Trustin Lee</a>
     * @version $Rev: 2152 $, $Date: 2010-02-17 17:22:45 +0900 (Wed, 17 Feb 2010) $
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Sharable {
        // no value
    }
}
