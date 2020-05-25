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
/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.netty.util.internal.logging;

import io.netty.util.internal.ObjectUtil;
import org.apache.commons.logging.Log;

/**
 * <a href="http://commons.apache.org/logging/">Apache Commons Logging</a>
 * logger.
 *
 * @deprecated Please use {@link Log4J2Logger} or {@link Log4JLogger} or
 * {@link Slf4JLogger}.
 */
@Deprecated
class CommonsLogger extends AbstractInternalLogger {

    private static final long serialVersionUID = 8647838678388394885L;

    private final transient Log logger;

    CommonsLogger(Log logger, String name) {
        super(name);
        this.logger = ObjectUtil.checkNotNull(logger, "logger");
    }

    /**
     * Delegates to the {@link Log#isTraceEnabled} method of the underlying
     * {@link Log} instance.
     */
    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    /**
     * Delegates to the {@link Log#trace(Object)} method of the underlying
     * {@link Log} instance.
     *
     * @param msg - the message object to be logged
     */
    @Override
    public void trace(String msg) {
        logger.trace(msg);
    }

    /**
     * Delegates to the {@link Log#trace(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     * </p>
     *
     * @param format
     *          the format string
     * @param arg
     *          the argument
     */
    @Override
    public void trace(String format, Object arg) {
        if (logger.isTraceEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            logger.trace(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#trace(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     * </p>
     *
     * @param format
     *          the format string
     * @param argA
     *          the first argument
     * @param argB
     *          the second argument
     */
    @Override
    public void trace(String format, Object argA, Object argB) {
        if (logger.isTraceEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            logger.trace(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#trace(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     * </p>
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void trace(String format, Object... arguments) {
        if (logger.isTraceEnabled()) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            logger.trace(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#trace(Object, Throwable)} method of
     * the underlying {@link Log} instance.
     *
     * @param msg
     *          the message accompanying the exception
     * @param t
     *          the exception (throwable) to log
     */
    @Override
    public void trace(String msg, Throwable t) {
        logger.trace(msg, t);
    }

    /**
     * Delegates to the {@link Log#isDebugEnabled} method of the underlying
     * {@link Log} instance.
     */
    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    //

    /**
     * Delegates to the {@link Log#debug(Object)} method of the underlying
     * {@link Log} instance.
     *
     * @param msg - the message object to be logged
     */
    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    /**
     * Delegates to the {@link Log#debug(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     * </p>
     *
     * @param format
     *          the format string
     * @param arg
     *          the argument
     */
    @Override
    public void debug(String format, Object arg) {
        if (logger.isDebugEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            logger.debug(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#debug(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     * </p>
     *
     * @param format
     *          the format string
     * @param argA
     *          the first argument
     * @param argB
     *          the second argument
     */
    @Override
    public void debug(String format, Object argA, Object argB) {
        if (logger.isDebugEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            logger.debug(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#debug(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     * </p>
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void debug(String format, Object... arguments) {
        if (logger.isDebugEnabled()) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            logger.debug(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#debug(Object, Throwable)} method of
     * the underlying {@link Log} instance.
     *
     * @param msg
     *          the message accompanying the exception
     * @param t
     *          the exception (throwable) to log
     */
    @Override
    public void debug(String msg, Throwable t) {
        logger.debug(msg, t);
    }

    /**
     * Delegates to the {@link Log#isInfoEnabled} method of the underlying
     * {@link Log} instance.
     */
    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /**
     * Delegates to the {@link Log#debug(Object)} method of the underlying
     * {@link Log} instance.
     *
     * @param msg - the message object to be logged
     */
    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    /**
     * Delegates to the {@link Log#info(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     * </p>
     *
     * @param format
     *          the format string
     * @param arg
     *          the argument
     */

    @Override
    public void info(String format, Object arg) {
        if (logger.isInfoEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            logger.info(ft.getMessage(), ft.getThrowable());
        }
    }
    /**
     * Delegates to the {@link Log#info(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     * </p>
     *
     * @param format
     *          the format string
     * @param argA
     *          the first argument
     * @param argB
     *          the second argument
     */
    @Override
    public void info(String format, Object argA, Object argB) {
        if (logger.isInfoEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            logger.info(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#info(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     * </p>
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void info(String format, Object... arguments) {
        if (logger.isInfoEnabled()) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            logger.info(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#info(Object, Throwable)} method of
     * the underlying {@link Log} instance.
     *
     * @param msg
     *          the message accompanying the exception
     * @param t
     *          the exception (throwable) to log
     */
    @Override
    public void info(String msg, Throwable t) {
        logger.info(msg, t);
    }

    /**
     * Delegates to the {@link Log#isWarnEnabled} method of the underlying
     * {@link Log} instance.
     */
    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    /**
     * Delegates to the {@link Log#warn(Object)} method of the underlying
     * {@link Log} instance.
     *
     * @param msg - the message object to be logged
     */
    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    /**
     * Delegates to the {@link Log#warn(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     * </p>
     *
     * @param format
     *          the format string
     * @param arg
     *          the argument
     */
    @Override
    public void warn(String format, Object arg) {
        if (logger.isWarnEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            logger.warn(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#warn(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     * </p>
     *
     * @param format
     *          the format string
     * @param argA
     *          the first argument
     * @param argB
     *          the second argument
     */
    @Override
    public void warn(String format, Object argA, Object argB) {
        if (logger.isWarnEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            logger.warn(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#warn(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     * </p>
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void warn(String format, Object... arguments) {
        if (logger.isWarnEnabled()) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            logger.warn(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#warn(Object, Throwable)} method of
     * the underlying {@link Log} instance.
     *
     * @param msg
     *          the message accompanying the exception
     * @param t
     *          the exception (throwable) to log
     */

    @Override
    public void warn(String msg, Throwable t) {
        logger.warn(msg, t);
    }

    /**
     * Delegates to the {@link Log#isErrorEnabled} method of the underlying
     * {@link Log} instance.
     */
    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    /**
     * Delegates to the {@link Log#error(Object)} method of the underlying
     * {@link Log} instance.
     *
     * @param msg - the message object to be logged
     */
    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    /**
     * Delegates to the {@link Log#error(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     * </p>
     *
     * @param format
     *          the format string
     * @param arg
     *          the argument
     */
    @Override
    public void error(String format, Object arg) {
        if (logger.isErrorEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, arg);
            logger.error(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#error(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     * </p>
     *
     * @param format
     *          the format string
     * @param argA
     *          the first argument
     * @param argB
     *          the second argument
     */
    @Override
    public void error(String format, Object argA, Object argB) {
        if (logger.isErrorEnabled()) {
            FormattingTuple ft = MessageFormatter.format(format, argA, argB);
            logger.error(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#error(Object)} method of the underlying
     * {@link Log} instance.
     *
     * <p>
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     * </p>
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void error(String format, Object... arguments) {
        if (logger.isErrorEnabled()) {
            FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            logger.error(ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Delegates to the {@link Log#error(Object, Throwable)} method of
     * the underlying {@link Log} instance.
     *
     * @param msg
     *          the message accompanying the exception
     * @param t
     *          the exception (throwable) to log
     */
    @Override
    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }
}
