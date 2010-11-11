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
 * A {@link ChannelEvent} which represents the notification of the completion
 * of a write request on a {@link Channel}.  This event is for going upstream
 * only.  Please refer to the {@link ChannelEvent} documentation to find out
 * what an upstream event and a downstream event are and what fundamental
 * differences they have.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2202 $, $Date: 2010-02-23 16:18:58 +0900 (Tue, 23 Feb 2010) $
 */
public interface WriteCompletionEvent extends ChannelEvent {
    /**
     * Returns the amount of data written.
     *
     * @return the number of written bytes or messages, depending on the
     *         type of the transport
     */
    long getWrittenAmount();
}
