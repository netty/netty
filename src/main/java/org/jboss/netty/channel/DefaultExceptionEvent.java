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

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.util.internal.StackTraceSimplifier;

/**
 * The default {@link ExceptionEvent} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class DefaultExceptionEvent implements ExceptionEvent {

    private final Channel channel;
    private final Throwable cause;

    /**
     * Creates a new instance.
     */
    public DefaultExceptionEvent(Channel channel, Throwable cause) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.channel = channel;
        this.cause = cause;
        StackTraceSimplifier.simplify(cause);
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getFuture() {
        return succeededFuture(getChannel());
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return getChannel().toString() + " EXCEPTION: " + cause;
    }
}
