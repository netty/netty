/*
 * Copyright 2024 The Netty Project
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
package io.netty.incubator.codec.http3;


import org.jetbrains.annotations.NotNull;

import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that all parameters and return values of methods within a package should be treated as non-null by
 * default, unless explicitly annotated with a nullness annotation such as {@link org.jetbrains.annotations.Nullable}.
 * <p>
 * This annotation is applied at the package level and affects all types within the annotated package.
 * <p>
 * Any nullness annotations explicitly applied to a parameter or return value within a package will override
 * the default nullness behavior specified by {@link NotNullByDefault}.
 */
@Documented
@TypeQualifierDefault({ ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
@NotNull
@interface NotNullByDefault {
}
