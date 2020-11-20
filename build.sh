#!/bin/bash

trap 'kill $(jobs -rp) 2>/dev/null' EXIT
set -ex

printThreadDumps() {
  jps -lv | while read I; do
    pid=`echo "$I" | awk '{ print $1; }'`
    echo
    echo "Thread dump process: $I";
    jstack -l $pid || true;
  done
}

java -version
mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true -B -V
( sleep $(( 60 * 5 ));
  printThreadDumps;
  sleep 10;
  printThreadDumps;
  sleep 10;
  printThreadDumps;
  echo "TIMEOUT"
  exit 1;
) &
threadDumpPid=$!
mvn test -B &
testPid=$!
wait $testPid
kill $threadDumpPid
