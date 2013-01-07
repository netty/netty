/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.transport.udt.nio;

import io.netty.logging.InternalLoggerFactory;
import io.netty.logging.Slf4JLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * shared test base
 */
public abstract class TestAny {

    protected static final Logger log = LoggerFactory.getLogger(TestAny.class);

    /**
     * use slf4j provider for io.netty.logging.InternalLogger
     */
    static {
        final InternalLoggerFactory defaultFactory = new Slf4JLoggerFactory();
        InternalLoggerFactory.setDefaultFactory(defaultFactory);
        log.info("InternalLoggerFactory={}", InternalLoggerFactory
                .getDefaultFactory().getClass().getName());
    }

}
