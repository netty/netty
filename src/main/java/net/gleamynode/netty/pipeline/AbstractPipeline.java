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

import java.util.NoSuchElementException;




/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class AbstractPipeline<E> implements Pipeline<E> {

    public void addAfter(String baseName, String name, PipeHandler<E> pipe) {
        addAfter(getOrDie(baseName), new DefaultPipe<E>(name, pipe));
    }

    public void addBefore(String baseName, String name, PipeHandler<E> pipe) {
        addBefore(getOrDie(baseName), new DefaultPipe<E>(name, pipe));
    }

    public void addFirst(String name, PipeHandler<E> pipe) {
        addFirst(new DefaultPipe<E>(name, pipe));
    }

    public void addLast(String name, PipeHandler<E> pipe) {
        addLast(new DefaultPipe<E>(name, pipe));
    }

    public Pipe<E> remove(String name) {
        return removeAndReturn(getOrDie(name));
    }

    public Pipe<E> remove(Class<? extends PipeHandler<E>> handlerType) {
        return removeAndReturn(getOrDie(handlerType));
    }

    public Pipe<E> replace(String oldName, String newName, PipeHandler<E> newHandler) {
        return replaceAndReturn(getOrDie(oldName), new DefaultPipe<E>(newName, newHandler));
    }

    public Pipe<E> replace(Class<? extends PipeHandler<E>> oldHandlerType, String newName, PipeHandler<E> newHandler) {
        return replaceAndReturn(getOrDie(oldHandlerType), new DefaultPipe<E>(newName, newHandler));
    }

    public PipeHandler<E> getFirstHandler() {
        return getHandlerSafely(getFirst());
    }

    public PipeHandler<E> getLastHandler() {
        return getHandlerSafely(getLast());
    }

    public PipeHandler<E> getHandler(Class<? extends PipeHandler<E>> handlerType) {
        return getHandlerSafely(get(handlerType));
    }

    public PipeHandler<E> getHandler(String name) {
        return getHandlerSafely(get(name));
    }

    public PipeContext<E> getContext(String name) {
        return getContextSafely(get(name));
    }

    public PipeContext<E> getContext(Class<? extends PipeHandler<E>> handlerType) {
        return getContextSafely(get(handlerType));
    }

    private PipeHandler<E> getHandlerSafely(Pipe<E> pipe) {
        if (pipe != null) {
            return pipe.getHandler();
        } else {
            return null;
        }
    }

    private PipeContext<E> getContextSafely(Pipe<E> pipe) {
        if (pipe != null) {
            return getContext(pipe);
        } else {
            return null;
        }
    }

    private Pipe<E> removeAndReturn(Pipe<E> pipe) {
        remove(pipe);
        return pipe;
    }

    private Pipe<E> replaceAndReturn(Pipe<E> oldPipe, Pipe<E> newPipe) {
        replace(oldPipe, newPipe);
        return oldPipe;
    }

    private Pipe<E> getOrDie(String name) {
        Pipe<E> pipe = get(name);
        if (pipe == null) {
            throw new NoSuchElementException(name);
        } else {
            return pipe;
        }
    }

    private Pipe<E> getOrDie(Class<? extends PipeHandler<E>> handlerType) {
        Pipe<E> pipe = get(handlerType);
        if (pipe == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return pipe;
        }
    }
}
