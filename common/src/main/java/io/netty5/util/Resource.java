/*
 * Copyright 2021 The Netty Project
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
package io.netty5.util;

/**
 * A resource that has a life-time, and can be {@linkplain #close() closed}.
 * Resources are initially {@linkplain #isAccessible() accessible}, but closing them makes them inaccessible.
 */
public interface Resource<T extends Resource<T>> extends AutoCloseable {
    /**
     * Send this object instance to another Thread, transferring the ownership to the recipient.
     * <p>
     * The object must be in a state where it can be sent, which includes at least being
     * {@linkplain #isAccessible() accessible}.
     * <p>
     * When sent, this instance will immediately become inaccessible, as if by {@linkplain #close() closing} it.
     * All attempts at accessing an object that has been sent, even if that object has not yet been received, should
     * cause an exception to be thrown.
     * <p>
     * Calling {@link #close()} on an object that has been sent will have no effect, so this method is safe to call
     * within a try-with-resources statement.
     */
    Send<T> send();

    /**
     * Close the resource, making it inaccessible.
     * <p>
     * Note, this method is not thread-safe unless otherwise specified.
     *
     * @throws IllegalStateException If this {@code Resource} has already been closed.
     */
    @Override
    void close();

    /**
     * Check if this object is accessible.
     *
     * @return {@code true} if this object is still valid and can be accessed,
     * otherwise {@code false} if, for instance, this object has been dropped/deallocated,
     * or been {@linkplain #send() sent} elsewhere.
     */
    boolean isAccessible();

    /**
     * Record the current access location for debugging purposes.
     * This information may be included if the resource throws a life-cycle related exception, or if it leaks.
     * If this resource has already been closed, then this method has no effect.
     *
     * @param hint An optional hint about this access and its context. May be {@code null}.
     * @return This resource instance.
     */
    @SuppressWarnings("unchecked")
    default T touch(Object hint) {
        return (T) this;
    }

    /**
     * Attempt to dispose of whatever the given object is.
     * <p>
     * If the object is {@link AutoCloseable}, such as anything that implements {@link Resource},
     * then it will be closed.
     * If the object is {@link ReferenceCounted}, then it will be released once.
     * <p>
     * Any exceptions caused by this will be left to bubble up, and checked exceptions will be wrapped in a
     * {@link RuntimeException}.
     *
     * @param obj The object to dispose of.
     */
    static void dispose(Object obj) {
        if (obj instanceof AutoCloseable) {
            try {
                ((AutoCloseable) obj).close();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Exception from closing object", e);
            }
        } else if (ReferenceCountUtil.isReferenceCounted(obj)) {
            ReferenceCountUtil.release(obj);
        }
    }

    /**
     * Check if an object is accessible. This returns {@code true} if the object is a {@linkplain Resource resource}
     * that {@linkplain Resource#isAccessible() is accessible}, or if the object is
     * {@linkplain ReferenceCounted reference counted} and has a positive
     * {@linkplain ReferenceCounted#refCnt() reference count}.
     * <p>
     * If the object is neither of these types, then the expected default value is returned.
     *
     * @param obj The object to check.
     * @param expectedDefault The value to return if the object is neither a resource, nor reference counted.
     * @return If the object is accessible.
     */
    static boolean isAccessible(Object obj, boolean expectedDefault) {
        if (obj instanceof Resource) {
            return ((Resource<?>) obj).isAccessible();
        }
        if (obj instanceof io.netty.util.ReferenceCounted) {
            return ((io.netty.util.ReferenceCounted) obj).refCnt() > 0;
        }
        return expectedDefault;
    }

    /**
     * Record the current access location for debugging purposes.
     * This information may be included if the resource throws a life-cycle related exception, or if it leaks.
     * If this resource has already been closed, then this method has no effect.
     * <p>
     * If the given object is not a resource, or not reference counted, then this method has no effect.
     *
     * @param obj The object to annotate.
     * @param hint An optional hint about this access and its context. May be {@code null}.
     */
    static void touch(Object obj, Object hint) {
        if (obj instanceof Resource) {
            ((Resource<?>) obj).touch(hint);
        } else if (obj instanceof io.netty.util.ReferenceCounted) {
            ((io.netty.util.ReferenceCounted) obj).touch(hint);
        }
    }
}
