/*
 * Copyright 2026 The Netty Project
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

/*
 * The machine dependent JNI definitions for 64-bit Windows, for cross compiling with a JDK that does not ship them
 * (docker/Dockerfile.cross_compile uses a Linux JDK), see docker/cross-compile/build.sh.
 *
 * Uses the same include guard as the jni_md.h of the JDK, so it can't be mixed with another one.
 */
#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#ifndef _WIN64
#error "This jni_md.h is for 64-bit Windows only"
#endif

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
/* __stdcall has no effect on x86_64. Newer JDKs define JNICALL (empty) in jni.h themselves, defining it the same way
   here keeps that from being a conflicting redefinition. */
#define JNICALL

typedef int jint;
typedef long long jlong;
typedef signed char jbyte;

#endif /* !_JAVASOFT_JNI_MD_H_ */
