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
 * Listens to the progress of a time-consuming I/O operation such as a large
 * file transfer.  If this listener is added to a {@link ChannelFuture} of an
 * I/O operation that supports progress notification, the listener's
 * {@link #operationProgressed(ChannelFuture, long, long, long)} method will be
 * called back by an I/O thread.  If the operation does not support progress
 * notification, {@link #operationProgressed(ChannelFuture, long, long, long)}
 * will not be invoked.  Like a usual {@link ChannelFutureListener} that this
 * interface extends, {@link #operationComplete(ChannelFuture)} will be called
 * when the future is marked as complete.
 *
 * <h3>Return the control to the caller quickly</h3>
 *
 * {@link #operationProgressed(ChannelFuture, long, long, long)} and
 * {@link #operationComplete(ChannelFuture)} is directly called by an I/O
 * thread.  Therefore, performing a time consuming task or a blocking operation
 * in the handler method can cause an unexpected pause during I/O.  If you need
 * to perform a blocking operation on I/O completion, try to execute the
 * operation in a different thread using a thread pool.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface ChannelFutureProgressListener extends ChannelFutureListener {

    /**
     * Invoked when the I/O operation associated with the {@link ChannelFuture}
     * has been progressed.
     *
     * @param future  the source {@link ChannelFuture} which called this
     *                callback
     */
    void operationProgressed(ChannelFuture future, long amount, long current, long total) throws Exception;
}
