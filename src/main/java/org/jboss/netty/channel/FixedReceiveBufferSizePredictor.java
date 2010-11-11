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
 * The {@link ReceiveBufferSizePredictor} that always yields the same buffer
 * size prediction.  This predictor ignores the feed back from the I/O thread.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class FixedReceiveBufferSizePredictor implements
        ReceiveBufferSizePredictor {

    private final int bufferSize;

    /**
     * Creates a new predictor that always returns the same prediction of
     * the specified buffer size.
     */
    public FixedReceiveBufferSizePredictor(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(
                    "bufferSize must greater than 0: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    public int nextReceiveBufferSize() {
        return bufferSize;
    }

    public void previousReceiveBufferSize(int previousReceiveBufferSize) {
        // Ignore
    }
}
