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
 * <a href="http://anonsvn.jboss.org/repos/common/common-logging-spi/">JBoss Logging</a>
 * loggers.
 */
public class JBossLoggerFactory extends InternalLoggerFactory<JBossLogger> {

    /**
     * Creates a new {@link JBossLogger} instance
     *
     * @param name the name of the new logger
     * @return the new {@link JBossLogger} instance
     */
    @Override
    public JBossLogger newInstance(String name) {
        final org.jboss.logging.Logger logger =
            org.jboss.logging.Logger.getLogger(name);
        return new JBossLogger(logger);
    }
}
