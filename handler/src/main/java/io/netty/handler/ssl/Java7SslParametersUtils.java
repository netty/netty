/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import javax.net.ssl.SSLParameters;
import java.security.AlgorithmConstraints;

final class Java7SslParametersUtils {

    private Java7SslParametersUtils() {
        // Utility
    }

    /**
     * Utility method that is used by {@link OpenSslEngine} and so allow use not not have any reference to
     * {@link AlgorithmConstraints} in the code. This helps us to not get into trouble when using it in java
     * version < 7 and especially when using on android.
     */
    static void setAlgorithmConstraints(SSLParameters sslParameters, Object algorithmConstraints) {
        sslParameters.setAlgorithmConstraints((AlgorithmConstraints) algorithmConstraints);
    }
}
