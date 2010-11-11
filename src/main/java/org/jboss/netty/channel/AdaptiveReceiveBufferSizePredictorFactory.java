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
 * The {@link ReceiveBufferSizePredictorFactory} that creates a new
 * {@link AdaptiveReceiveBufferSizePredictor}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class AdaptiveReceiveBufferSizePredictorFactory implements
        ReceiveBufferSizePredictorFactory {

    private final int minimum;
    private final int initial;
    private final int maximum;

    /**
     * Creates a new factory with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     */
    public AdaptiveReceiveBufferSizePredictorFactory() {
        this(AdaptiveReceiveBufferSizePredictor.DEFAULT_MINIMUM,
                AdaptiveReceiveBufferSizePredictor.DEFAULT_INITIAL,
                AdaptiveReceiveBufferSizePredictor.DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new factory with the specified parameters.
     *
     * @param minimum  the inclusive lower bound of the expected buffer size
     * @param initial  the initial buffer size when no feed back was received
     * @param maximum  the inclusive upper bound of the expected buffer size
     */
    public AdaptiveReceiveBufferSizePredictorFactory(int minimum, int initial, int maximum) {
        if (minimum <= 0) {
            throw new IllegalArgumentException("minimum: " + minimum);
        }
        if (initial < minimum) {
            throw new IllegalArgumentException("initial: " + initial);
        }
        if (maximum < initial) {
            throw new IllegalArgumentException("maximum: " + maximum);
        }

        this.minimum = minimum;
        this.initial = initial;
        this.maximum = maximum;
    }

    public ReceiveBufferSizePredictor getPredictor() throws Exception {
        return new AdaptiveReceiveBufferSizePredictor(minimum, initial, maximum);
    }
}
