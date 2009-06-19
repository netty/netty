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

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;

/**
 * A set of configuration properties of a {@link Channel}.
 * <p>
 * Please down-cast to more specific configuration type such as
 * {@link SocketChannelConfig} or use {@link #setOptions(Map)} to set the
 * transport-specific properties:
 * <pre>
 * Channel ch = ...;
 * SocketChannelConfig cfg = <strong>(SocketChannelConfig) ch.getConfig();</strong>
 * cfg.setTcpNoDelay(false);
 * </pre>
 *
 * <h3>Option map</h3>
 *
 * An option map property is a dynamic write-only property which allows
 * the configuration of a {@link Channel} without down-casting its associated
 * {@link ChannelConfig}.  To update an option map, please call {@link #setOptions(Map)}.
 * <p>
 * All {@link ChannelConfig} has the following options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "bufferFactory"}</td><td>{@link #setBufferFactory(ChannelBufferFactory)}</td>
 * </tr><tr>
 * <td>{@code "connectTimeoutMillis"}</td><td>{@link #setConnectTimeoutMillis(int)}</td>
 * </tr><tr>
 * <td>{@code "pipelineFactory"}</td><td>{@link #setPipelineFactory(ChannelPipelineFactory)}</td>
 * </tr>
 * </table>
 * <p>
 * More options are available in the sub-types of {@link ChannelConfig}.  For
 * example, you can configure the parameters which are specific to a TCP/IP
 * socket as explained in {@link SocketChannelConfig} or {@link NioSocketChannelConfig}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.ChannelPipelineFactory
 * @apiviz.composedOf org.jboss.netty.channel.ReceiveBufferSizePredictor
 *
 * @apiviz.excludeSubtypes
 */
public interface ChannelConfig {

    /**
     * Sets the configuration properties from the specified {@link Map}.
     */
    void setOptions(Map<String, Object> options);

    /**
     * Sets a configuration property with the specified name and value.
     * To override this method properly, you must call the super class:
     * <pre>
     * public boolean setOption(String name, Object value) {
     *     if (super.setOption(name, value)) {
     *         return true;
     *     }
     *
     *     if (name.equals("additionalOption")) {
     *         ....
     *         return true;
     *     }
     *
     *     return false;
     * }
     * </pre>
     *
     * @return {@code true} if and only if the property has been set
     */
    boolean setOption(String name, Object value);

    /**
     * Returns the default {@link ChannelBufferFactory} used to create a new
     * {@link ChannelBuffer}.  The default is {@link HeapChannelBufferFactory}.
     * You can specify a different factory to change the default
     * {@link ByteOrder} for example.
     */
    ChannelBufferFactory getBufferFactory();

    /**
     * Sets the default {@link ChannelBufferFactory} used to create a new
     * {@link ChannelBuffer}.  The default is {@link HeapChannelBufferFactory}.
     * You can specify a different factory to change the default
     * {@link ByteOrder} for example.
     */
    void setBufferFactory(ChannelBufferFactory bufferFactory);

    /**
     * Returns the {@link ChannelPipelineFactory} which will be used when
     * a child channel is created.  If the {@link Channel} does not create
     * a child channel, this property is not used at all, and therefore will
     * be ignored.
     */
    ChannelPipelineFactory getPipelineFactory();

    /**
     * Sets the {@link ChannelPipelineFactory} which will be used when
     * a child channel is created.  If the {@link Channel} does not create
     * a child channel, this property is not used at all, and therefore will
     * be ignored.
     */
    void setPipelineFactory(ChannelPipelineFactory pipelineFactory);

    /**
     * Returns the connect timeout of the channel in milliseconds.  If the
     * {@link Channel} does not support connect operation, this property is not
     * used at all, and therefore will be ignored.
     *
     * @return the connect timeout in milliseconds.  {@code 0} if disabled.
     */
    int getConnectTimeoutMillis();

    /**
     * Sets the connect timeout of the channel in milliseconds.  If the
     * {@link Channel} does not support connect operation, this property is not
     * used at all, and therefore will be ignored.
     *
     * @param connectTimeoutMillis the connect timeout in milliseconds.
     *                             {@code 0} to disable.
     */
    void setConnectTimeoutMillis(int connectTimeoutMillis);

    /**
     * @deprecated Use {@link WriteTimeoutHandler} instead.
     *
     * Returns the write timeout of the channel in milliseconds.  If a write
     * operation is not completed within the write timeout, an
     * {@link IOException} will be raised.  If the {@link Channel} does not
     * support write operation, this property is not used at all, and therefore
     * will be ignored.
     *
     * @return the write timeout in milliseconds.  {@code 0} if disabled.
     */
    @Deprecated
    int getWriteTimeoutMillis();

    /**
     * @deprecated Use {@link WriteTimeoutHandler} instead.
     *
     * Sets the write timeout of the channel in milliseconds.  If a write
     * operation is not completed within the write timeout, an
     * {@link IOException} will be raised.  If the {@link Channel} does not
     * support write operation, this property is not used at all, and therefore
     * will be ignored.
     *
     * @param writeTimeoutMillis the write timeout in milliseconds.
     *                           {@code 0} to disable.
     */
    @Deprecated
    void setWriteTimeoutMillis(int writeTimeoutMillis);
}
