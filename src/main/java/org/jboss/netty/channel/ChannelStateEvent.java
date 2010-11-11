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
 * A {@link ChannelEvent} which represents the change of the {@link Channel}
 * state.  It can mean the notification of a change or the request for a
 * change, depending on whether it is an upstream event or a downstream event
 * respectively.  Please refer to the {@link ChannelEvent} documentation to
 * find out what an upstream event and a downstream event are and what
 * fundamental differences they have.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @apiviz.has org.jboss.netty.channel.ChannelState
 */
public interface ChannelStateEvent extends ChannelEvent {

    /**
     * Returns the changed property of the {@link Channel}.
     */
    ChannelState getState();

    /**
     * Returns the value of the changed property of the {@link Channel}.
     * Please refer to {@link ChannelState} documentation to find out the
     * allowed values for each property.
     */
    Object getValue();
}
