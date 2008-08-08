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
    void addFirst (String name, PipeHandler<E> pipe);
    void addLast  (String name, PipeHandler<E> pipe);
    void addBefore(String baseName, String name, PipeHandler<E> pipe);
    void addAfter (String baseName, String name, PipeHandler<E> pipe);

    void remove(Pipe<E> pipe);
    void remove(String name);
    Pipe<E> removeFirst();
    Pipe<E> removeLast();

    void replace(Pipe<E> oldPipe, Pipe<E> newPipe);

    Pipe<E> getByName(String name);
    Pipe<E> getByHandlerType(Class<? extends PipeHandler<E>> handlerType);

    PipeContext<E> getContext(Pipe<E> pipe);

    void sendUpstream(E element);
    void sendDownstream(E element);

    PipelineSink<E> getSink();
    void setSink(PipelineSink<E> sink);
}
