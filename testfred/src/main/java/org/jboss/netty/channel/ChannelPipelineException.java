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

/**
 * A {@link ChannelException} which is thrown when a {@link ChannelPipeline}
 * failed to process a {@link ChannelEvent} or when a {@link ChannelPipelineFactory}
 * failed to initialize a {@link ChannelPipeline}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.hidden
 */
public class ChannelPipelineException extends ChannelException {

    private static final long serialVersionUID = 3379174210419885980L;

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(Throwable cause) {
        super(cause);
    }
}
