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

/**
 * The default {@link WriteCompletionEvent} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2210 $, $Date: 2010-03-04 08:11:39 +0900 (Thu, 04 Mar 2010) $
 */
public class DefaultWriteCompletionEvent implements WriteCompletionEvent {

    private final Channel channel;
    private final long writtenAmount;

    /**
     * Creates a new instance.
     */
    public DefaultWriteCompletionEvent(Channel channel, long writtenAmount) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (writtenAmount <= 0) {
            throw new IllegalArgumentException(
                    "writtenAmount must be a positive integer: " + writtenAmount);
        }

        this.channel = channel;
        this.writtenAmount = writtenAmount;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getFuture() {
        return succeededFuture(getChannel());
    }

    public long getWrittenAmount() {
        return writtenAmount;
    }

    @Override
    public String toString() {
        String channelString = getChannel().toString();
        StringBuilder buf = new StringBuilder(channelString.length() + 32);
        buf.append(channelString);
        buf.append(" WRITTEN_AMOUNT: ");
        buf.append(getWrittenAmount());
        return buf.toString();
    }
}
