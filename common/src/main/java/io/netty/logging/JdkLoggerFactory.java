/*
 * Copyright 2012 The Netty Project
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
package io.netty.logging;


/**
 * A logger factory which creates
 * <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/logging/">java.util.logging</a>
 * loggers.
 */
public class JdkLoggerFactory extends InternalLoggerFactory<JdkLogger> {

    /**
     * Creates a new {@link JdkLogger} instance
     *
     * @param name the name of the new {@link JdkLogger}
     * @return a new {@link JdkLogger} instance
     */
    @Override
    public JdkLogger newInstance(String name) {
        final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(name);
        return new JdkLogger(logger, name);
    }
}
