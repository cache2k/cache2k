# cache2k – High Performance Java Caching

cache2k focuses on providing a well engineered in-memory object cache implementation for
Java applications. The implementation leads back to a project started in 2000, hence
the name cache2k.

## News

  * **Version 0.20, 2014-11-05**: Revised configuration, iterator, improved 
    exception handling, see [Version 0.20 release notes](0/20.html)
  * **Version 0.19.1, 2014-03-13**: Rerelease, now on Maven Central!
  * **Version 0.19, 2014-02-25**: JMX support enabled, bulk API enhancements,
    simple transaction support, see [Version 0.19 release notes](0/19.html)
  * **Version 0.18, 2013-12-18**: Initial open source release

## Status

We use every cache2k release within production environments. However, some of the basic features
are still evolving and there may be API breaking changes until we reach version 1.0.

## Integrating cache2k in your project

chacke2k is on maven central. If you use maven, add to your project pom:

```xml
<dependencies>
  <dependency>
    <groupId>org.cache2k</groupId>
    <artifactId>cache2k-api</artifactId>
    <version>${cache2k-version}</version>
  </dependency>
  <dependency>
      <groupId>org.cache2k</groupId>
      <artifactId>cache2k-core</artifactId>
      <version>${cache2k-version}</version>
      <scope>runtime</scope>
  </dependency>
</dependencies>
```

Please replace `${cache2k-version}` with the latest version. The cache2k-core 
module is compatible with Java 6 environments including android.

For Java enterprise applications, add this to your dependencies: 

```xml
<dependencies>
  <dependency>
    <groupId>org.cache2k</groupId>
    <artifactId>cache2k-api</artifactId>
    <version>${cache2k-version}</version>
  </dependency>
  <dependency>
      <groupId>org.cache2k</groupId>
      <artifactId>cache2k-ee</artifactId>
      <version>${cache2k-version}</version>
      <scope>runtime</scope>
  </dependency>
</dependencies>
```

This will add JMX support.  

## Getting started

Here are two quick examples how to construct a cache and destroy it again.

### cache-aside / direct cache manipulation

For direct cache manipulation you can use the methods peek() and put():

```java
Cache<String,String> c =
  CacheBuilder.newCache(String.class, String.class).build();
String val = c.peek("something");
assertNull(val);
c.put("something", "hello");
val = c.get("something");
assertNotNull(val);
c.destroy();
```

### read-through caching

Instead of direct manipulation it is possible to register a cache source. If no
data is available within the cache, the cache will delegate to the cache source:

```java
CacheSource<String,Integer> _lengthCountingSource =
  new CacheSource<String, Integer>() {
    @Override
    public Integer get(String o) throws Throwable {
      return o.length();
    }
  };
Cache<String,Integer> c =
  CacheBuilder.newCache(String.class, Integer.class)
    .source(_lengthCountingSource)
    .build();
int v = c.get("hallo");
assertEquals(5, v);
v = c.get("long string");
assertEquals(11, v);
c.destroy();
```

Mind, that the methods get() and peek() have distinct behaviour in cache2k. get() will always
do its best to get the date, peek() will only return data if the cache contains it. The
presence of the cache source does not change this behaviour.

## Features

The main aim is to get a small footprint core cache implementation which does

 * Fast
 * Even faster with lock free cache access (experimental), see [the benchmarks page](benchmarks.html)
 * Exception support
 * Null value support
 * Expiry/update on time
 * Background refreshing
 * Nice statistics
 * JMX
 * ...

Since it is fresh open source, we will put up a detailed description of each feature as well
polish the API and semantics one after another. See our [todo list](todo.html)

## Feedback

Please use the issue tracker for bugs and wishes you step upon. We also monitor the stackoverflow.com tag
**cache2k** for general usage questions.

## License

The code is released under GPLv3. If you want to use the code in an open source project that needs
a license other than GPL please raise an issue.

## Alternatives

cache2k does not try to be a full blown distributed enterprise caching solution. Some alternatives
which you should take a look on:

 * Google Guava Cache
 * EHCache
 * JCS
 * JBoss Infinispan

## Credits

  * Andrius Velykis for his [maven reflow theme](http://andriusvelykis.github.io/reflow-maven-skin)
  * Maximilian Richt for customizing the reflow theme and polishing the cache2k.org site design
  * Dr. Song Jiang for support, [Hompage of Dr. Song Jiang](http://www.ece.eng.wayne.edu/~sjiang)
  * Dawid Weiss for the [JUnit Benchmarks](http://labs.carrotsearch.com/junit-benchmarks.html) extension
