/*
 * Copyright 2011 The Netty Project
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
package io.netty.logging;

import biz.massivedynamics.modger.Logger;
import biz.massivedynamics.modger.message.MessageType;

/**
 * An implementation of a <a href="https://launchpad.net/modger">Modger</a> logger
 */
public class ModgerLogger extends AbstractInternalLogger {
    
    /**
     * Creates a new Modger logger
     * 
     * @param name The name to use
     */
    public ModgerLogger(String name) {
        this.logger = new Logger(name);
    }
    
    /**
     * Creates a new Modger logger
     * 
     * @param logger The logger to use
     */
    public ModgerLogger(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * The logger being used
     */
    private Logger logger;
    
    /**
     * Gets the logger being used
     * 
     * @return The logger being used
     */
    public Logger getLogger() {
        return this.logger;
    }

    /**
     * Checks to see if debug is enabled
     * 
     * @return True if enabled, otherwise false
     */
    @Override
    public boolean isDebugEnabled() {
        return !MessageType.DEBUG.isFiltered();
    }

    /**
     * Checks to see if information is enabled
     * 
     * @return True if enabled, otherwise false
     */
    @Override
    public boolean isInfoEnabled() {
        return !MessageType.INFORMATION.isFiltered();
    }

    /**
     * Checks to see if warnings are enabled
     * 
     * @return True if enabled, otherwise false
     */
    @Override
    public boolean isWarnEnabled() {
        return !MessageType.WARNING.isFiltered();
    }

    /**
     * Checks to see if errors are enabled
     * 
     * @return True if enabled, otherwise false
     */
    @Override
    public boolean isErrorEnabled() {
        return !MessageType.ERROR.isFiltered();
    }

    /**
     * Submits a debug message
     * 
     * @param msg The message
     */
    @Override
    public void debug(String msg) {
        this.logger.submitDebug(msg);
    }

    /**
     * Submits a debug message
     * 
     * @param msg The message
     * @param cause The cause
     */
    @Override
    public void debug(String msg, Throwable cause) {
        this.logger.submitDebug(msg, cause);
    }

    /**
     * Submits a message containing information
     * 
     * @param msg The message
     */
    @Override
    public void info(String msg) {
        this.logger.submitInformation(msg);
    }

    /**
     * Submits a message containing information
     * 
     * @param msg The message
     * @param cause The cause
     */
    @Override
    public void info(String msg, Throwable cause) {
        this.logger.submitInformation(msg, cause);
    }

    /**
     * Submits a warning message
     * 
     * @param msg The warning
     */
    @Override
    public void warn(String msg) {
        this.logger.submitWarning(msg);
    }

    /**
     * Submits a warning message
     * 
     * @param msg The message
     * @param cause The cause
     */
    @Override
    public void warn(String msg, Throwable cause) {
        this.logger.submitWarning(msg, cause);
    }

    /**
     * Submits an error message
     * 
     * @param msg The error message
     */
    @Override
    public void error(String msg) {
        this.logger.submitError(msg);
    }

    /**
     * Submits an error message
     * 
     * @param msg The message
     * @param cause The cause
     */
    @Override
    public void error(String msg, Throwable cause) {
        this.logger.submitError(msg, cause);
    }
    
    /**
     * Gets a string-based representation of this logger
     * 
     * @return The logger's name
     */
    @Override
    public String toString() {
        return this.logger.getName();
    }
    
}
