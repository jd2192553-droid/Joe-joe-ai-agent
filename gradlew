#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls -ld "$PRG"
    link=$(expr "$PRG" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/"$link"
    fi
done
SAVED="$(cd "$(dirname \"$PRG\")" >/dev/null 2>&1 && pwd)"
APP_HOME="$(dirname \"$SAVED\")"

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn ( ) {
    echo "$*" >&2
}

die ( ) {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
darwin=false
msys=false
cygwin=false
nogwin=false
case "$( uname )" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* | MINGW* )
    msys=true
    ;;
  GNU/Linux)
    nogwin=true
    ;;
esac

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$msys" && ! "$nogwin" ; then
    case $( ulimit -S -n ) in       #(
      'unlimited'|*'      '* ) :; #(
      ?* ) ulimit -S -n 262144 ;;
    esac
fi

# Collect all arguments for the java command;
# * $DEFAULT_JVM_OPTS, $JAVA_OPTS, and $GRADLE_OPTS can contain fragments of
# shell commands we need to be careful to let shells expand those fragments
# and to pass the resulting string to the JVM as a single argument
# * Put the JVM user-defined options in, so they take precedence

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" should not continue processing the returned file list.
if [ -n "$BASH_VERSION" ] || [ -n "$ZSH_VERSION" ] ; then
    # Try the string as a location for the gradle.properties file
    GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}" && export GRADLE_USER_HOME
    # For Cygwin or MSYS, switch paths to Windows format before running java
    if "$cygwin" || "$msys" ; then
        GRADLE_HOME=$( cygpath --windows "$GRADLE_HOME" )
        APP_HOME=$( cygpath --windows "$APP_HOME" )
    fi
fi

# Now convert the arguments - kludge to limit ourselves to /bin/sh
i=0
for arg in "$@" ; do
    arg_count=$((i+1))
    case $arg in                                #(
      -*)   set -- "$@" "$arg"
            shift
            ;;
      *     ) set -- "$@" "$arg"
              shift
              ;;
    esac
done

exec "$JAVACMD" "${JVM_OPTS[@]}" "${DEFAULT_JVM_OPTS[@]}" \
        "${GRADLE_OPTS[@]}" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"
