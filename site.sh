#!/bin/bash

set -e
set -x

# speed up maven startup
# https://zeroturnaround.com/rebellabs/your-maven-build-is-slow-speed-it-up/
export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"

DEST=target/site;

mvn -Dmaven.test.skip=true clean compile site

# install stable version of 1.0 documentation
# don't do that any more 12 Sep 2020;jw
if false; then
  VERSION=1.0.2.Final
  DOC=$DEST/docs/1.0
  mkdir -p $DOC/apidocs
  cp documentation/generated-user-guide-archive-1.0/* $DOC
  for I in cache2k-api cache2k-jmx-api cache2k-jcache-api; do
    fn=~/.m2/repository/org/cache2k/$I/$VERSION/$I-$VERSION-javadoc.jar;
    test -f $fn || mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.0:get -Dartifact=org.cache2k:$I:$VERSION:jar:javadoc
    mkdir -p $DOC/apidocs/$I/
    ( cd  $DOC/apidocs/$I/; jar xf $fn )
  done
fi

DOC=$DEST/docs/jcache/1.1.0/apidocs/cache-api
# JSR107 JCache
fn=~/.m2/repository/javax/cache/cache-api/1.1.0/cache-api-1.1.0-javadoc.jar;
test -f $fn || mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.0:get -Dartifact=javax.cache:cache-api:1.1.0:jar:javadoc
mkdir -p $DOC
( cd  $DOC; jar xf $fn )

# 1.0 is latest
# cp -a $DEST/docs/1.0 $DEST/docs/latest

# latest is always the latest and greatest including dev stuff, since we want to put links
# to it
DOC=$DEST/docs/latest
mkdir -p $DOC
cp documentation/target/generated-docs/user-* $DOC
for I in cache2k-api cache2k-spring cache2k-micrometer cache2k-jmx cache2k-addon cache2k-jcache; do
  mkdir -p $DOC/apidocs/$I;
  cp -a $I/target/site/apidocs/* $DOC/apidocs/$I/;
done

mkdir $DEST/schema;
cp -a cache2k-schema/src/main//resources/org/cache2k/schema/cache2k-core-v1.x.xsd $DEST/schema/cache2k-core-v1.x.xsd
cp -a cache2k-schema/src/main//resources/org/cache2k/schema/cache2k.xsd $DEST/schema/cache2k-v2.x.xsd
