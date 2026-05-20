def sourceFile = new File(project.basedir.parentFile,
        "transport-classes-io_uring/src/main/java/io/netty/channel/uring/IoUringIoHandler.java")
def outputRoot = new File(project.build.directory, "generated-test-sources/iouring-handler-shadow")
def outputFile = new File(outputRoot, "io/netty/channel/uring/IoUringIoHandler.java")

if (!sourceFile.isFile()) {
    throw new IllegalStateException("Unable to find IoUringIoHandler source: " + sourceFile)
}

def source = sourceFile.getText("UTF-8")
def loggerAnchor = """    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringIoHandler.class);
"""
def hookField = loggerAnchor + "    static volatile Runnable wakeupBeforeEventFdWriteHook;\n"
def wakeupAnchor = ~/(?m)(        if \(!executor\.isExecutorThread\(Thread\.currentThread\(\)\) &&\r?\n\s+!eventfdAsyncNotify\.getAndSet\(true\)\) \{\r?\n)/
def hook =
        "            Runnable wakeupHook = wakeupBeforeEventFdWriteHook;\n" +
        "            if (wakeupHook != null) {\n" +
        "                wakeupHook.run();\n" +
        "            }\n"

def loggerAnchorFirst = source.indexOf(loggerAnchor)
if (loggerAnchorFirst < 0) {
    throw new IllegalStateException("Unable to find logger anchor in " + sourceFile)
}

def matcher = wakeupAnchor.matcher(source)
if (!matcher.find()) {
    throw new IllegalStateException("Unable to find wakeup hook anchor in " + sourceFile)
}

def generated = source.replace(loggerAnchor, hookField)
generated = wakeupAnchor.matcher(generated).replaceFirst('\$1' + hook)
if (!generated.contains("static volatile Runnable wakeupBeforeEventFdWriteHook;")) {
    throw new IllegalStateException("Generated IoUringIoHandler does not contain the wakeup test hook field")
}
if (!generated.contains("Runnable wakeupHook = wakeupBeforeEventFdWriteHook;")) {
    throw new IllegalStateException("Generated IoUringIoHandler does not contain the wakeup test hook call")
}

outputRoot.deleteDir()
outputFile.parentFile.mkdirs()
outputFile.setText(generated, "UTF-8")
log.info("Generated test shadow for IoUringIoHandler at " + outputFile)
