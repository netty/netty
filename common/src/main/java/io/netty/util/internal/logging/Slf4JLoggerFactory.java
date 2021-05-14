/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal.logging;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

/**
 * Logger factory which creates a <a href="https://www.slf4j.org/">SLF4J</a>
 * logger.
 */
public class Slf4JLoggerFactory extends InternalLoggerFactory {

    @SuppressWarnings("deprecation")
    public static final InternalLoggerFactory INSTANCE = new Slf4JLoggerFactory();

    static final InternalLoggerFactory INSTANCE_WITH_NOP_CHECK = new Slf4JLoggerFactory(true);

    /**
     * @deprecated Use {@link #INSTANCE} instead.
     */
    @Deprecated
    public Slf4JLoggerFactory() {
    }

    Slf4JLoggerFactory(boolean failIfNOP) {
        assert failIfNOP; // Should be always called with true.
        if (LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory) {
            throw new NoClassDefFoundError("NOPLoggerFactory not supported");
        }
    }

    @Override
    public InternalLogger newInstance(String name) {
        return wrapLogger(LoggerFactory.getLogger(name));
    }

    // package-private for testing.
    static InternalLogger wrapLogger(Logger logger) {
        return logger instanceof LocationAwareLogger ?
                new LocationAwareSlf4JLogger((LocationAwareLogger) logger) : new Slf4JLogger(logger);
    }
}
