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
package org.jboss.netty.handler.codec.replay;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * An {@link Exception} which is thrown when a user calls an unsupported
 * operation on a {@link ChannelBuffer} in a {@link ReplayingDecoder}
 * implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class UnreplayableOperationException extends
        UnsupportedOperationException {

    private static final long serialVersionUID = 8577363912862364021L;

    /**
     * Creates a new instance.
     */
    public UnreplayableOperationException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public UnreplayableOperationException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public UnreplayableOperationException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new instance.
     */
    public UnreplayableOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
