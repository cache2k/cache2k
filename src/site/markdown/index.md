# cache2k – High Performance Java Caching

cache2k focuses on providing a well engineered in-memory object cache implementation for
Java applications. 

[![GitHub Stars](https://x.h7e.eu/badges/xz/q/github/starGazers/gh-stargazers/cache2k/cache2k)](https://github.com/cache2k/cache2k/stargazers)
[![License](https://x.h7e.eu/badges/xz/txt/license/apache)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Stack Overflow](https://x.h7e.eu/badges/xz/txt/stackoverflow/cache2k)](https://stackoverflow.com/questions/tagged/cache2k)
![Maven Central](https://x.h7e.eu/badges/xz/q/maven/latestVersion/maven-central/org.cache2k/cache2k-api)

````java
  Cache<String,String> cache = new Cache2kBuilder<String, String>() {}
    .expireAfterWrite(5, TimeUnit.MINUTES)    // expire/refresh after 5 minutes
    .resilienceDuration(30, TimeUnit.SECONDS) // cope with at most 30 seconds
                                              // outage before propagating 
                                              // exceptions
    .refreshAhead(true)                       // keep fresh when expiring
    .loader(this::expensiveOperation)         // auto populating function
    .build();
````

For a detailed introduction continue with [Getting Started](docs/1.0/user-guide.html#getting-started).

## Features at a glance

 * Single small jar file (less than 400k) with no external dependencies
 * Even smaller, for use with [Android](docs/1.0/user-guide.html#android)
 * One of the fastest cache for JVM local caching, see [the benchmarks page](benchmarks.html)
 * Java 6 and [Android](docs/1.0/user-guide.html#android) compatible
 * Leverages Java 8 to increase performance (if possible)
 * Pure Java code, no use of `sun.misc.Unsafe`
 * Thread safe, with a complete set of [atomic operations](docs/1.0/user-guide.html#atomic-operations)
 * [Resilience and smart exception handling](docs/1.0/user-guide.html#resilience-and-exceptions) 
 * Null value support, see [User Guide - Null Values](docs/1.0/user-guide.html#null-values)
 * Automatic [Expiry and Refresh](docs/1.0/user-guide.html#expiry-and-refresh): duration or point in time, variable expiry per entry, delta calculations
 * CacheLoader with blocking read through, see [User Guide - Loading and Read Through](docs/1.0/user-guide.html#loading-read-through)
 * CacheWriter
 * [Event listeners](docs/1.0/user-guide.html#event-listeners)
 * [Refresh ahead](docs/1.0/user-guide.html#refresh-ahead) reduces latency
 * [Low Overhead Statistics](docs/1.0/user-guide.html#statistics) and JMX support
 * [Separate and defined API](docs/1.0/apidocs/cache2k-api/index.html) with stable and concise interface
 * [complete JCache / JSR107 support](docs/1.0/user-guide.html#jcache)
 * [XML based configuration](docs/1.0/user-guide.html#xml-configuration), to separate cache tuning from logic

## News

  * **Version 1.0.0.CR3, 2017-01-17**: Cleanups&Documentation, improvements for Java 8, See [Version 1.0.0.CR3 release notes](1/0.0.CR3.html)
  * **Version 1.0.0.CR2, 2016-12-22**: Remove deprecated methods. Cleanups. Documentation. See [Version 1.0.0.CR2 release notes](1/0.0.CR2.html)
  * **Version 1.0.0.CR1, 2016-12-05**: More cleanups, XML configuration has arrived. See [Version 1.0.0.CR1 release notes](1/0.0.CR1.html)
  * **Version 0.28-BETA, 2016-09-02**: Minor bug fixes, SLF4J Support, statistics cleanup. See [Version 0.28 release notes](0/28.html)
  * **Version 0.27-BETA, 2016-07-26**: Performance improvements, leveraging Java 8, more API restructuring, cleanup and minor bug fixes. See [Version 0.27 release notes](0/27.html)
  * **Version 0.26-BETA, 2016-05-06**: Resilience policy, more API restructuring, cleanup and minor bug fixes. See [Version 0.26 release notes](0/26.html)

## Integrating cache2k in your project

chacke2k is on maven central. Maven users add the following dependencies:

```xml
  <dependency>
    <groupId>org.cache2k</groupId>
    <artifactId>cache2k-api</artifactId>
    <version>${cache2k-version}</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>org.cache2k</groupId>
    <artifactId>cache2k-all</artifactId>
    <version>${cache2k-version}</version>
    <scope>runtime</scope>
  </dependency>
```

Please replace `${cache2k-version}` with the latest version.

## Feedback

Please use the [GitHub issue tracker](https://github.com/cache2k/cache2k) for bugs and wishes you step upon. We also monitor the stackoverflow.com tag
**cache2k** for general usage questions.

## License

The code is released under Apache license. 

## Alternatives

cache2k does not try to be a full blown distributed enterprise caching solution. Some alternatives
which you should take a look on:

 * Google Guava Cache
 * EHCache
 * JCS
 * Caffeine
 
Distributed caches:

 * Infinispan
 * hazelcast
 * redsisson
 * Apache ignite

## Credits

  * Andrius Velykis for his [maven reflow theme](http://andriusvelykis.github.io/reflow-maven-skin)
  * Maximilian Richt for customizing the reflow theme and polishing the cache2k.org site design
  * Dr. Song Jiang for support, [Hompage of Dr. Song Jiang](http://www.ece.eng.wayne.edu/~sjiang)
