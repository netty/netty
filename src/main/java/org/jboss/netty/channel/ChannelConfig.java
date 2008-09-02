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
import java.util.Map;

import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;


/**
 * The configuration properties of a {@link Channel}.
 * <p>
 * Please down-cast to the transport-specific configuration type such as
 * {@link NioSocketChannelConfig} or use {@link #setOptions(Map)} to set the
 * transport-specific properties.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.ChannelPipelineFactory
 */
public interface ChannelConfig {

    /**
     * Sets the configuration properties from the specified {@link Map}.
     */
    void setOptions(Map<String, Object> options);

    /**
     * Returns the {@link ChannelPipelineFactory} which will be used when
     * a child channel is created.  If the {@link Channel} doesn't create
     * a child channel, this property is not used at all, and therefore will
     * be ignored.
     */
    ChannelPipelineFactory getPipelineFactory();

    /**
     * Sets the {@link ChannelPipelineFactory} which will be used when
     * a child channel is created.  If the {@link Channel} doesn't create
     * a child channel, this property is not used at all, and therefore will
     * be ignored.
     */
    void setPipelineFactory(ChannelPipelineFactory pipelineFactory);

    /**
     * Returns the connect timeout of the channel in milliseconds.  If the
     * {@link Channel} doesn't support connect operation, this property is not
     * used at all, and therefore will be ignored.
     *
     * @return the connect timeout in milliseconds.  {@code 0} if disabled.
     */
    int getConnectTimeoutMillis();

    /**
     * Sets the connect timeout of the channel in milliseconds.  If the
     * {@link Channel} doesn't support connect operation, this property is not
     * used at all, and therefore will be ignored.
     *
     * @param connectTimeoutMillis the connect timeout in milliseconds.
     *                             {@code 0} to disable.
     */
    void setConnectTimeoutMillis(int connectTimeoutMillis);

    /**
     * Returns the write timeout of the channel in milliseconds.  If a write
     * operation is not done within the write timeout, an {@link IOException}
     * will be raised.  If the {@link Channel} doesn't support write operation,
     * this property is not used at all, and therefore will be ignored.
     *
     * @return the write timeout in milliseconds.  {@code 0} if disabled.
     */
    int getWriteTimeoutMillis();

    /**
     * Sets the write timeout of the channel in milliseconds.  If a write
     * operation is not done within the write timeout, an {@link IOException}
     * will be raised.  If the {@link Channel} doesn't support write operation,
     * this property is not used at all, and therefore will be ignored.
     *
     * @param writeTimeoutMillis the write timeout in milliseconds.
     *                           {@code 0} to disable.
     */
    void setWriteTimeoutMillis(int writeTimeoutMillis);
}
