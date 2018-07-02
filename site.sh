#!/bin/bash

set -e
set -x

# speed up maven startup
# https://zeroturnaround.com/rebellabs/your-maven-build-is-slow-speed-it-up/
export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"

DEST=target/site;

mvn -Psite -Dmaven.test.skip=true -T 1.5C clean compile site

# install stable version of 1.0 documentation
VERSION=1.0.2.Final
DOC=$DEST/docs/1.0
mkdir -p $DOC/apidocs
cp documentation/generated-user-guide-archive-1.0/* $DOC
for I in cache2k-api cache2k-jmx-api cache2k-jcache-api; do
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.0:get -Dartifact=org.cache2k:$I:$VERSION:jar:javadoc
  mkdir -p $DOC/apidocs/$I/
  ( cd  $DOC/apidocs/$I/; jar xf ~/.m2/repository/org/cache2k/$I/$VERSION/$I-$VERSION-javadoc.jar )
done

# 1.0 is latest
# cp -a $DEST/docs/1.0 $DEST/docs/latest

# latest is always the latest and greatest including dev stuff, since we want to put links
# to it
DOC=$DEST/docs/latest
mkdir -p $DOC
cp documentation/target/generated-docs/user-* $DOC
for I in cache2k-api; do
  mkdir -p $DOC/apidocs/$I;
  cp -a $I/target/site/apidocs/* $DOC/apidocs/$I/;
done
