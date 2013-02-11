/*
 * Copyright 2013 The Netty Project
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

package io.netty.util.internal;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;

public abstract class TypeParameterMatcher {

    public static TypeParameterMatcher find(
            final Object object, final Class<?> parameterizedSuperClass) {
        return find(object, parameterizedSuperClass, 0);
    }

    public static TypeParameterMatcher find(
            final Object object, final Class<?> parameterizedSuperClass, int index) {
        TypeResolver resolver = new TypeResolver();
        ResolvedType type = resolver.resolve(object.getClass());
        final ResolvedType superType = type.typeParametersFor(parameterizedSuperClass).get(index);
        return new TypeParameterMatcher() {
            @Override
            public boolean match(Object msg) {
                return superType.getErasedType().isInstance(msg);
            }
        };
    }

    public abstract boolean match(Object msg);

    protected TypeParameterMatcher() { }
}
