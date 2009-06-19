/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * An <a href="http://en.wikipedia.org/wiki/Hypertext_Transfer_Protocol">HTTP</a>
 * chunk which is used for HTTP chunked transfer-encoding.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public interface HttpChunk {

    /**
     * The 'end of content' maker in chunked encoding.
     */
    static HttpChunk LAST_CHUNK = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);

    /**
     * Returns {@code true} if and only if this chunk is the 'end of content'
     * marker.
     */
    boolean isLast();

    /**
     * Returns the content of this chunk.  If this is the 'end of content'
     * maker, {@link ChannelBuffers#EMPTY_BUFFER} will be returned.
     */
    ChannelBuffer getContent();
}
