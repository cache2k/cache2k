#!/bin/bash

trap 'kill $(jobs -p) 2>/dev/null' EXIT
set -ex

java -version
mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true -B -V
( sleep $(( 60 * 1 )); kill -3 `jps -q` ) &
threadDumpPid=$!
mvn test -B &
testPid=$!
wait $testPid
kill $threadDumpPid
