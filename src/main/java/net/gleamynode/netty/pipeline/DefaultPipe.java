/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.pipeline;

import static net.gleamynode.netty.pipeline.PipelineCoverage.*;

import java.lang.annotation.AnnotationFormatError;
import java.util.logging.Logger;

public class DefaultPipe<E> implements Pipe<E> {
    private static final Logger logger = Logger.getLogger(DefaultPipe.class.getName());

    private final String name;
    private final PipeHandler<E> handler;
    private final boolean canHandleUpstream;
    private final boolean canHandleDownstream;

    public DefaultPipe(String name, PipeHandler<E> handler) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        canHandleUpstream = handler instanceof UpstreamHandler;
        canHandleDownstream = handler instanceof DownstreamHandler;

        if (!canHandleUpstream && !canHandleDownstream) {
            throw new IllegalArgumentException(
                    "handler must be either " +
                    UpstreamHandler.class.getName() + " or " +
                    DownstreamHandler.class.getName() + '.');
        }

        PipelineCoverage coverage = handler.getClass().getAnnotation(PipelineCoverage.class);
        if (coverage == null) {
            logger.warning(
                    "Handler '" + handler.getClass().getName() +
                    "' doesn't have a '" +
                    PipelineCoverage.class.getSimpleName() +
                    "' annotation with its class declaration. " +
                    "It is recommended to add the annotation to tell if " +
                    "one handler instance can handle more than one pipeline " +
                    "(\"" + ALL + "\") or not (\"" + ONE + "\")");
        } else {
            String coverageValue = coverage.value();
            if (coverageValue == null) {
                throw new AnnotationFormatError(
                        PipelineCoverage.class.getSimpleName() +
                        " annotation value is undefined for type: " +
                        handler.getClass().getName());
            }

            if (!coverageValue.equals(ALL) && !coverageValue.equals(ONE)) {
                throw new AnnotationFormatError(
                        PipelineCoverage.class.getSimpleName() +
                        " annotation value: " + coverageValue +
                        " (must be either \"" + ALL + "\" or \"" + ONE + ")");
            }
        }

        this.name = name;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public PipeHandler<E> getHandler() {
        return handler;
    }

    public boolean canHandleDownstream() {
        return canHandleDownstream;
    }

    public boolean canHandleUpstream() {
        return canHandleUpstream;
    }
}