/*
 * Copyright 2013 The Netty Project
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
package io.netty.util;

/**
 * A reference-counted object that requires explicit deallocation.
 * 一个需要进行显式资源回收的用于引用计数的对象
 * <p>
 * When a new {@link ReferenceCounted} is instantiated, it starts with the reference count of {@code 1}.
 * {@link #retain()} increases the reference count, and {@link #release()} decreases the reference count.
 * If the reference count is decreased to {@code 0}, the object will be deallocated explicitly, and accessing
 * the deallocated object will usually result in an access violation.
 * </p>
 * 当一个实现了引用计数接口的对象被实例化时，它的引用计数是从1开始的。
 * 通过retain()来增加引用计数，以及release()方法减少引用计数；
 * 当这个引用计数减少到0时，这个对象将会被显式的回收，并且访问这个被回收的对象通常会导致一个访问违背。
 * <p>
 * If an object that implements {@link ReferenceCounted} is a container of other objects that implement
 * {@link ReferenceCounted}, the contained objects will also be released via {@link #release()} when the container's
 * reference count becomes 0.
 * </p>
 * 如果一个实现了ReferenceCounted接口的对象是一个其他实现了ReferenceCounted对象的容器，
 * 当这个容器的引用计数变成0的话，那么这些被包含的对象也会通过调用release()方法被释放掉，
 */
public interface ReferenceCounted {
    /**
     * Returns the reference count of this object.  If {@code 0}, it means this object has been deallocated.
     * 返回这个对象的引用计数，当为0的时候，它以为着这个对象已经被回收了
     */
    int refCnt();

    /**
     * Increases the reference count by {@code 1}.
     * 增加引用计数，增加幅度为1
     */
    ReferenceCounted retain();

    /**
     * Increases the reference count by the specified {@code increment}.
     * 增加引用计数，增加幅度为特定的increment参数值。
     */
    ReferenceCounted retain(int increment);

    /**
     * Records the current access location of this object for debugging purposes.
     * If this object is determined to be leaked, the information recorded by this operation will be provided to you
     * via {@link ResourceLeakDetector}.  This method is a shortcut to {@link #touch(Object) touch(null)}.
     *
     */
    ReferenceCounted touch();

    /**
     * Records the current access location of this object with an additional arbitrary information for debugging
     * purposes.  If this object is determined to be leaked, the information recorded by this operation will be
     * provided to you via {@link ResourceLeakDetector}.
     *
     */
    ReferenceCounted touch(Object hint);

    /**
     * Decreases the reference count by {@code 1} and deallocates this object if the reference count reaches at
     * {@code 0}.
     *
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release();

    /**
     * Decreases the reference count by the specified {@code decrement} and deallocates this object if the reference
     * count reaches at {@code 0}.
     * 减少引用计数，减少指定的decrement值，如果引用计数达到0的话，则回收这个对象
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release(int decrement);
}
