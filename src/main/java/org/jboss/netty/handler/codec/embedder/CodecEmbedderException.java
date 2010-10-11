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
package org.jboss.netty.handler.codec.embedder;

/**
 * A {@link RuntimeException} which is thrown when a {@link CodecEmbedder}
 * failed to encode or decode the specified input.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 *
 * @apiviz.exclude
 */
public class CodecEmbedderException extends RuntimeException {

    private static final long serialVersionUID = -6283302594160331474L;

    /**
     * Creates a new instance.
     */
    public CodecEmbedderException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public CodecEmbedderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public CodecEmbedderException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public CodecEmbedderException(Throwable cause) {
        super(cause);
    }
}
