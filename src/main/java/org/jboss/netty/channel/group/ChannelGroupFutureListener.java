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
package org.jboss.netty.channel.group;

import java.util.EventListener;

/**
 * Listens to the result of a {@link ChannelGroupFuture}.  The result of the
 * asynchronous {@link ChannelGroup} I/O operations is notified once this
 * listener is added by calling {@link ChannelGroupFuture#addListener(ChannelGroupFutureListener)}
 * and all I/O operations are complete.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface ChannelGroupFutureListener extends EventListener {

    /**
     * Invoked when all I/O operations associated with the
     * {@link ChannelGroupFuture} have been completed.
     *
     * @param future  The source {@link ChannelGroupFuture} which called this
     *                callback.
     */
    void operationComplete(ChannelGroupFuture future) throws Exception;
}
