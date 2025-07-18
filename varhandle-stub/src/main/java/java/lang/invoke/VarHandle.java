/*
 * Copyright 2025 The Netty Project
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
package java.lang.invoke;

/**
 * A stub for the VarHandle class.<br>
 * This stub is used to allow Java 8 release compilation to work as expected.
 * The sole limit of this stub is that since {@code java.lang.invoke} is a privileged package
 * it cannot be used at runtime (e.g. loaded by a classloader).<p>
 * For example, if {@code SomeClass} is loaded at runtime:
 * <pre>
 *     class SomeClass {
 *          private static final VarHandle VH = // ... obtained somehow;
 *
 *          public static void storeStoreFence() {
 *              if (VH == null) {
 *                  return;
 *              }
 *              VH.storeStoreFence();
 *          }
 *     }
 * </pre>
 * this is not going to work on Java 8.<p>
 * To fix it is possible to use an holder class (which won't be loaded at runtime):
 * <pre>
 *     class SomeClass {
 *          static class Holder {
 *              private static final VarHandle VH = // ... obtained somehow;
 *          }
 *
 *          public static void storeStoreFence() {
 *              VarHandle vh = Holder.VH;
 *              if (vh == null) {
 *                  return;
 *              }
 *              vh.storeStoreFence();
 *          }
 *     }
 * </pre>
 * Or:
 * <pre>
 *     class SomeClass {
 *          private static final Object VH = // ... obtained somehow;
 *
 *          public static void storeStoreFence() {
 *              if (VH == null) {
 *                  return;
 *              }
 *              ((VarHandle)VH).storeStoreFence();
 *          }
 *     }
 * </pre>
 *
 * The reason why the methods on the stub are declared as native is to allow
 * {@link java.lang.invoke.MethodHandle.PolymorphicSignature} to work as expected,
 * see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-15.html#jls-15.12.3">JLS 15.12.3</a>:<br>
 * <pre>
 *   A method is signature polymorphic if all of the following are true:
 *   - It is declared in the java.lang.invoke.MethodHandle class or the java.lang.invoke.VarHandle class.
 *   - It has a single variable arity parameter (§8.4.1) whose declared type is Object[].
 *   - It is native.
 * </pre>
 * This seems counter-intuitive since this stub is not going to be used at runtime, but it is required to allow Java 8
 * compilation to produce {@code VarHandle}'s method invocations with parameters and result types with
 * the types of the formal ones of the compile-time declaration.
 *
 */
public class VarHandle {

    @MethodHandle.PolymorphicSignature
    public native Object get(Object... args);

    @MethodHandle.PolymorphicSignature
    public native Object getAcquire(Object... args);

    @MethodHandle.PolymorphicSignature
    public native void set(Object... args);

    @MethodHandle.PolymorphicSignature
    public native void setRelease(Object... args);

    @MethodHandle.PolymorphicSignature
    public native Object getAndAdd(Object... args);

    @MethodHandle.PolymorphicSignature
    public native boolean compareAndSet(Object... args);

    public static void storeStoreFence() {
        throw new UnsupportedOperationException("Not implemented in varhandle-stub");
    }
}
