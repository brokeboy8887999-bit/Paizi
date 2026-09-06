#!/usr/bin/env sh

#
# Gradle start up script for POSIX.
#

PRG="$0"

while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done

SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null 2>&1
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null 2>&1

APP_BASE_NAME=`basename "$0"`

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: Java could not be found."
    echo "Please check JAVA_HOME."
    exit 1
fi

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: Gradle wrapper JAR not found:"
    echo "$CLASSPATH"
    exit 1
fi

# JVM options
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Combine JVM options.
# Do NOT put literal quote characters around the options.
JVM_OPTS="$DEFAULT_JVM_OPTS"

if [ -n "$JAVA_OPTS" ]; then
    JVM_OPTS="$JVM_OPTS $JAVA_OPTS"
fi

if [ -n "$GRADLE_OPTS" ]; then
    JVM_OPTS="$JVM_OPTS $GRADLE_OPTS"
fi

# Execute Gradle Wrapper.
exec "$JAVACMD" $JVM_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
