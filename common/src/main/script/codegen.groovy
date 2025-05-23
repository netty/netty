/*
 * Copyright 2023 The Netty Project
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

String[] templateDirs = [properties["collection.template.dir"],
                         properties["collection.template.test.dir"]]
String[] outputDirs = [properties["collection.src.dir"],
                       properties["collection.testsrc.dir"]]

templateDirs.eachWithIndex { templateDir, i ->
    convertSources templateDir, outputDirs[i]
    copyMiscs templateDir, outputDirs[i]
}

void convertSources(String templateDir, String outputDir) {
    String[] keyPrimitives = ["byte", "char", "short", "int", "long"]
    String[] keyObjects = ["Byte", "Character", "Short", "Integer", "Long"]
    String[] keyNumberMethod = ["byteValue", "charValue", "shortValue", "intValue", "longValue"]

    keyPrimitives.eachWithIndex { keyPrimitive, i ->
        convertTemplates templateDir, outputDir, keyPrimitive, keyObjects[i], keyNumberMethod[i]
    }
}

void convertTemplates(String templateDir,
                      String outputDir,
                      String keyPrimitive,
                      String keyObject,
                      String keyNumberMethod) {
    def keyName = keyPrimitive.capitalize()
    def replaceFrom = "(^.*)K([^.]+)\\.template\$"
    def replaceTo = "\\1" + keyName + "\\2.java"
    def hashCodeFn = keyPrimitive.equals("long") ? "(int) (key ^ (key >>> 32))" : "(int) key"
    ant.copy(todir: outputDir) {
        fileset(dir: templateDir) {
            include(name: "**/*.template")
        }
        filterset() {
            filter(token: "K", value: keyName)
            filter(token: "k", value: keyPrimitive)
            filter(token: "O", value: keyObject)
            filter(token: "KEY_NUMBER_METHOD", value: keyNumberMethod)
            filter(token: "HASH_CODE", value: hashCodeFn)
        }
        regexpmapper(from: replaceFrom, to: replaceTo)
    }
}

void copyMiscs(String templateDir, String outputDir) {
    ant.copy(todir: outputDir) {
        fileset(dir: templateDir) {
            exclude(name: "**/*.template")
        }
    }
}