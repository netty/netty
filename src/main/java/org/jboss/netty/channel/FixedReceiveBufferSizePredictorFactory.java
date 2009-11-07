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
 * The {@link ReceiveBufferSizePredictorFactory} that returns a
 * {@link FixedReceiveBufferSizePredictor} with the pre-defined configuration.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class FixedReceiveBufferSizePredictorFactory implements
        ReceiveBufferSizePredictorFactory {

    private final ReceiveBufferSizePredictor predictor;

    /**
     * Creates a new factory that returns a {@link FixedReceiveBufferSizePredictor}
     * which always returns the same prediction of the specified buffer size.
     */
    public FixedReceiveBufferSizePredictorFactory(int bufferSize) {
        predictor = new FixedReceiveBufferSizePredictor(bufferSize);
    }

    public ReceiveBufferSizePredictor getPredictor() throws Exception {
        return predictor;
    }
}
