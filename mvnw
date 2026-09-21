#!/bin/sh
# Maven wrapper launcher script
# Source: https://github.com/takari/maven-wrapper

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD=java
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

BASEDIR=$(dirname "$0")
MVNWDIR="$BASEDIR/.mvn/wrapper"
WRAPPER_JAR="$MVNWDIR/maven-wrapper.jar"
WRAPPER_PROPERTIES="$MVNWDIR/maven-wrapper.properties"

DISTRIBUTION_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES")
MAVEN_HOME="${MAVEN_USER_HOME:-${HOME}/.m2/wrapper/dists}/$(basename "${DISTRIBUTION_URL%.zip}")"

if [ -d "$MAVEN_HOME" ] ; then
  MVNEXEC="$MAVEN_HOME/bin/mvn"
else
  exec "$JAVACMD" \
    -jar "$WRAPPER_JAR" \
    --baseDir "$BASEDIR" \
    "$@"
fi

exec "$MVNEXEC" "$@"
