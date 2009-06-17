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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Predicts the number of readable bytes in the receive buffer of a
 * {@link Channel}.
 * <p>
 * It calculates the close-to-optimal capacity of the {@link ChannelBuffer}
 * for the next read operation depending on the actual number of read bytes
 * in the previous read operation.  More accurate the prediction is, more
 * effective the memory utilization will be.
 * <p>
 * Once a read operation is performed and the actual number of read bytes is
 * known, an I/O thread will call {@link #previousReceiveBufferSize(int)} to
 * update the predictor so it can predict more accurately next time.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public interface ReceiveBufferSizePredictor {

    /**
     * Predicts the capacity of the {@link ChannelBuffer} for the next
     * read operation depending on the actual number of read bytes in the
     * previous read operation.
     *
     * @return the expected number of readable bytes this time
     */
    int nextReceiveBufferSize();

    /**
     * Updates this predictor by specifying the actual number of read bytes
     * in the previous read operation.
     *
     * @param previousReceiveBufferSize
     *        the actual number of read bytes in the previous read operation
     */
    void previousReceiveBufferSize(int previousReceiveBufferSize);
}
