String[] templateDirs = [properties["collection.template.dir"],
                         properties["collection.template.test.dir"]]
String[] outputDirs = [properties["collection.src.dir"],
                       properties["collection.testsrc.dir"]]

templateDirs.eachWithIndex { templateDir, i ->
    convertSources templateDir, outputDirs[i]
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