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
package org.jboss.netty.handler.codec.frame;

/**
 * An {@link Exception} which is thrown when the length of the frame
 * decoded by {@link DelimiterBasedFrameDecoder} is greater than the maximum.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 * @apiviz.hidden
 */
public class TooLongFrameException extends Exception {

    private static final long serialVersionUID = -1995801950698951640L;

    /**
     * Creates a new instance.
     */
    public TooLongFrameException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(Throwable cause) {
        super(cause);
    }
}
