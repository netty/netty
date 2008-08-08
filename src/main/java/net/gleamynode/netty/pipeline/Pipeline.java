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

public interface Pipeline<E> extends Iterable<Pipe<E>> {
    void addFirst (Pipe<E> pipe);
    void addLast  (Pipe<E> pipe);
    void addBefore(Pipe<E> basePipe, Pipe<E> pipe);
    void addAfter (Pipe<E> basePipe, Pipe<E> pipe);
    void addFirst (String name, PipeHandler<E> handler);
    void addLast  (String name, PipeHandler<E> handler);
    void addBefore(String baseName, String name, PipeHandler<E> handler);
    void addAfter (String baseName, String name, PipeHandler<E> handler);

    void remove(Pipe<E> pipe);
    Pipe<E> remove(String name);
    Pipe<E> remove(Class<? extends PipeHandler<E>> handlerType);
    Pipe<E> removeFirst();
    Pipe<E> removeLast();

    void replace(Pipe<E> oldPipe, Pipe<E> newPipe);
    Pipe<E> replace(String oldName, String newName, PipeHandler<E> newHandler);
    Pipe<E> replace(Class<? extends PipeHandler<E>> oldHandlerType, String newName, PipeHandler<E> newHandler);

    Pipe<E> getFirst();
    Pipe<E> getLast();
    PipeHandler<E> getFirstHandler();
    PipeHandler<E> getLastHandler();

    Pipe<E> get(String name);
    Pipe<E> get(Class<? extends PipeHandler<E>> handlerType);
    PipeHandler<E> getHandler(String name);
    PipeHandler<E> getHandler(Class<? extends PipeHandler<E>> handlerType);

    PipeContext<E> getContext(Pipe<E> pipe);
    PipeContext<E> getContext(String name);
    PipeContext<E> getContext(Class<? extends PipeHandler<E>> handlerType);


    void sendUpstream(E element);
    void sendDownstream(E element);

    PipelineSink<E> getSink();
    void setSink(PipelineSink<E> sink);
}
