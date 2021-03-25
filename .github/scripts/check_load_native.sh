#!/bin/bash
set -e

if [ "$#" -ne 3 ]; then
    echo "Expected jar directory, classname and method"
    exit 1
fi

TEMP_FILE=$(mktemp)

cat <<EOT > "$TEMP_FILE"
public class Load {
    public static void main(String... args) {
        try {
            System.out.println("Invoking " + args[0] + "." + args[1] + "();");
            Class<?> clazz = Class.forName(args[0]);
            clazz.getDeclaredMethod(args[1]).invoke(null);
        } catch (Throwable cause) {
            System.out.println("Native loading failed");
            cause.printStackTrace();
            System.exit(1);
        }
        System.out.println("Native loading successful");
    }
}
EOT

JAVA_FILE="$TEMP_FILE".java
mv "$TEMP_FILE" "$JAVA_FILE"
CLASSPATH=$(find "$1" -name '*.jar' | grep -v tests.jar | grep -v sources.jar | tr '\n' ':')
java -cp "$CLASSPATH" "$JAVA_FILE" "$2" "$3"
EXIT_CODE=$?
rm "$JAVA_FILE"
exit "$EXIT_CODE"