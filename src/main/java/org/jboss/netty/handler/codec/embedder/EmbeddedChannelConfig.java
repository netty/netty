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
package org.jboss.netty.handler.codec.embedder;

import java.util.Map;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelPipelineFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
class EmbeddedChannelConfig implements ChannelConfig {

    static final ChannelConfig INSTANCE = new EmbeddedChannelConfig();

    private EmbeddedChannelConfig() {
        super();
    }

    public int getConnectTimeoutMillis() {
        return 0;
    }

    public ChannelPipelineFactory getPipelineFactory() {
        return null;
    }

    public int getWriteTimeoutMillis() {
        return 0;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        // Unused
    }

    public void setOptions(Map<String, Object> options) {
        // Unused
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        // Unused
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        // Unused
    }
}
