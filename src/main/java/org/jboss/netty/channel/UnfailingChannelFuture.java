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

/**
 * The {@link ChannelFuture} which can not fail at all.  Any attempt to mark
 * this future as failure, by calling {@link #setFailure(Throwable)} will raise
 * an {@link IllegalStateException}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class UnfailingChannelFuture extends DefaultChannelFuture {

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     * @param cancellable
     *        {@code true} if and only if this future can be canceled
     */
    public UnfailingChannelFuture(Channel channel, boolean cancellable) {
        super(channel, cancellable);
    }

    @Override
    public boolean setFailure(Throwable cause) {
        throw new IllegalStateException("Can not fail", cause);
    }
}
