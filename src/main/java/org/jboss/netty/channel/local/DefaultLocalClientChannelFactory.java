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
package org.jboss.netty.channel.local;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

/**
 * The default {@link LocalClientChannelFactory} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 */
public class DefaultLocalClientChannelFactory implements LocalClientChannelFactory {

    private final ChannelSink sink;

    /**
     * Creates a new instance.
     */
    public DefaultLocalClientChannelFactory() {
        sink = new LocalClientChannelSink();
    }

    public LocalChannel newChannel(ChannelPipeline pipeline) {
        return new DefaultLocalChannel(null, this, pipeline, sink, null);
    }

    /**
     * Does nothing because this implementation does not require any external
     * resources.
     */
    public void releaseExternalResources() {
        // No external resources.
    }
}
