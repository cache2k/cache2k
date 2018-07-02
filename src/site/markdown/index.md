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
 * [complete JCache / JSR107 support, complatible to JCache Specification 1.1](docs/1.0/user-guide.html#jcache)
 * [XML based configuration](docs/1.0/user-guide.html#xml-configuration), to separate cache tuning from logic

## News

  * **Version 1.1.1.Alpha, 2018-07-02**: Development preview release, new jar file structure, See [Version 1.1.1.Alpha release notes](1/1.1.Alpha.html)
  * **Version 1.0.2.Final, 2017-12-29**: Support JCache standard 1.1, See [Version 1.0.2.Final release notes](1/0.2.Final.html)
  * **Version 1.0.1.Final, 2017-08-14**: minor bugfix release, See [Version 1.0.1.Final release notes](1/0.1.Final.html)
  * **Version 1.0.0.Final, 2017-07-11**: Minor documentation tweaks, allow more chars in a cache name, See [Version 1.0.0.Final release notes](1/0.0.Final.html)

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
