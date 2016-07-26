# cache2k – High Performance Java Caching

cache2k focuses on providing a well engineered in-memory object cache implementation for
Java applications. The implementation leads back to a project started in 2000, hence
the name cache2k.

## Features at a glance

The main goal is a small footprint cache implementation.

 * One of the fastest cache for JVM local caching, see [the benchmarks page](benchmarks.html)
 * Java 6 and Android compatible
 * Leverages Java 8 to increase performance, when available
 * Portable Java code, no use of `sun.misc.Unsafe`
 * Resilience: Smart exception handling
 * Null value support
 * Expiry: duration or point in time
 * Variable expiry per entry
 * CacheLoader with blocking read through
 * CacheWriter
 * Events
 * Refresh ahead
 * Build-in, efficient statistics
 * JMX support
 * Separate API with concise interface
 * JCache support

## Status

We use every cache2k release within production environments. However, some of the basic features
are still evolving and there may be API breaking changes until we reach version 1.0.

## Road map

  * _1.0_, finalize JSR107 JCache support, stabilize API, documentation (July 2016)
  * _1.2_, improve Bulk performance, reduce threads needed for timing
  * _1.4_, async support
  * _1.6_, persistence and off-heap features
  
The road map represents a rough plan, the dates and features may change.

## News

  * **Version 0.26-BETA, 2016-05-06**: Resilience policy, more API restructuring, cleanup and minor bug fixes. See [Version 0.26 release notes](0/26.html)
  * **Version 0.25-BETA, 2016-04-21**: API restructuring, expiry and asynchronous notifications. See [Version 0.25 release notes](0/25.html)
  * **Version 0.24-BETA, 2016-04-11**: JSR107 support, Restructuring, Storage removed, Events, CacheWriter, etc. See [Version 0.24 release notes](0/24.html)
  * **Version 0.23.1, 2016-04-07**: Switch to Apache license, bug fix. See [Version 0.23.1 release notes](0/23.1.html)
  * **Version 0.23, 2016-02-10**: OSGi support, ClockProPlus eviction becomes default. See [Version 0.23 release notes](0/23.html)
  * **Version 0.22.1, 2016-01-13**: Bugfix release, Update recommended. See [Version 0.22.1 release notes](0/22.1.html)
  * **Version 0.22, 2015-12-29**: Minor bug fix release. See [Version 0.22 release notes](0/22.html)
  * **Version 0.21.1, 2015-06-13**: Fix Android compatibility. Closes GH#19
  * **Version 0.21, 2015-02-04**: Rewrite internal entry locking. Bunch of new methods and interfaces. See [Version 0.21 release notes](0/21.html)
  * 2015-02-01: FOSDEM talk, mostly covering advanced eviction techniques and latest benchmarks with Version 0.21, see [slides on slideshare](http://www.slideshare.net/cruftex/cache2k-java-caching-turbo-charged-fosdem-2015) 
  * **Version 0.20, 2014-11-11**: Revised configuration, iterator, improved 
    exception handling, android compatible core module, see [Version 0.20 release notes](0/20.html)
  * **Version 0.19.2, 2014-09-23**: Rerelease of 0.19, compatible to Android. 
    JMX support is dropped in this version. JMX support will be available in an extra jar in the future. 
  * **Version 0.19.1, 2014-03-13**: Re-release, now on Maven Central!
  * **Version 0.19, 2014-02-25**: JMX support enabled, bulk API enhancements,
    simple transaction support, see [Version 0.19 release notes](0/19.html)
  * **Version 0.18, 2013-12-18**: Initial open source release


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

For Java enterprise applications, you can use a OSGi enabled bundle (cache2k-all) that contains everything in a single jar: 

```xml
<dependencies>
  <dependency>
    <groupId>org.cache2k</groupId>
    <artifactId>cache2k-api</artifactId>
    <version>${cache2k-version}</version>
    <scope>compile</scope>
  </dependency>
  <dependency>
      <groupId>org.cache2k</groupId>
      <artifactId>cache2k-all</artifactId>
      <version>${cache2k-version}</version>
      <scope>runtime</scope>
  </dependency>
</dependencies>
```

Please mind that `cache2k-all` also contains the `cache2k-api` module, hence `cache2k-api` is only needed at compile time.

## Getting started

Here are two quick examples how to construct a cache and destroy it again.

### cache-aside / direct cache manipulation

```java
Cache<String,String> cache = new Cache2kBuilder<String, String>() {}.build();
String val = cache.peek("something");
assertNull(val);
cache.put("something", "hello");
val = cache.get("something");
assertNotNull(val);
cache.dclose();
```

### read-through caching

Instead of direct manipulation it is possible to register a cache source. If no
data is available within the cache, the cache will delegate to the cache source:

```java
CacheLoader<String, Integer> lengthCountingLoader =
  new CacheLoader<String, Integer>() {
    @Override
    public Integer get(String o) {
      return o.length();
    }
  };
Cache<String, Integer> cache =
  new Cache2kBuilder<String, Integer>() {}
    .loader(lengthCountingLoader)
    .build();
int v = cache.get("hallo");
assertEquals(5, v);
assertNull(cache.peek("long string"));
v = cache.get("long string");
assertEquals(11, v);
assertEquals(11, cache.peek("long string"));
cache.close();
```

Mind, that the methods get() and peek() have distinct behaviour in cache2k. get() will invoke the loader, 
peek() will only return data if the cache contains it.

## Feedback

Please use the issue tracker for bugs and wishes you step upon. We also monitor the stackoverflow.com tag
**cache2k** for general usage questions.

## License

The code is released under Apache license. 

## Alternatives

cache2k does not try to be a full blown distributed enterprise caching solution. Some alternatives
which you should take a look on:

 * Google Guava Cache
 * EHCache
 * JCS
 * JBoss Infinispan
 * Caffeine

## Credits

  * Andrius Velykis for his [maven reflow theme](http://andriusvelykis.github.io/reflow-maven-skin)
  * Maximilian Richt for customizing the reflow theme and polishing the cache2k.org site design
  * Dr. Song Jiang for support, [Hompage of Dr. Song Jiang](http://www.ece.eng.wayne.edu/~sjiang)
  * Dawid Weiss for the [JUnit Benchmarks](http://labs.carrotsearch.com/junit-benchmarks.html) extension
