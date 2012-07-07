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
 * <a href="http://logging.apache.org/log4j/1.2/index.html">Apache Log4J</a>
 * loggers.
 */
public class Log4JLoggerFactory extends InternalLoggerFactory<Log4JLogger> {

    /**
     * Creates a new {@link Log4JLogger} instance
     *
     * @param name the name of the new instance
     * @return a new {@link Log4JLogger} instance
     */
    @Override
    public Log4JLogger newInstance(String name) {
        final org.apache.log4j.Logger logger =
            org.apache.log4j.Logger.getLogger(name);
        return new Log4JLogger(logger);
    }
}
