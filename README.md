[![License](https://x.h7e.eu/badges/xz/txt/license/apache)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Stack Overflow](https://x.h7e.eu/badges/xz/txt/stackoverflow/cache2k)](https://stackoverflow.com/questions/tagged/cache2k)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.cache2k/cache2k-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.cache2k/cache2k-core)

# cache2k Java Caching

cache2k is an in-memory high performance Java Caching library.

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

For a detailed introduction continue with [Getting Started](https://cache2k.org/docs/1.0/user-guide.html#getting-started).

## Features at a glance

 * Single small jar file (less than 400k) with no external dependencies
 * Even smaller, for use with [Android](https://cache2k.org/docs/1.0/user-guide.html#android)
 * One of the fastest cache for JVM local caching, see [the benchmarks page](https://cache2k.org/benchmarks.html)
 * Java 6 and [Android](https://cache2k.org/docs/1.0/user-guide.html#android) compatible
 * Leverages Java 8 to increase performance (if possible)
 * Pure Java code, no use of `sun.misc.Unsafe`
 * Thread safe, with a complete set of [atomic operations](https://cache2k.org/docs/1.0/user-guide.html#atomic-operations)
 * [Resilience and smart exception handling](https://cache2k.org/docs/1.0/user-guide.html#resilience-and-exceptions) 
 * Null value support, see [User Guide - Null Values](https://cache2k.org/docs/1.0/user-guide.html#null-values)
 * Automatic [Expiry and Refresh](https://cache2k.org/docs/1.0/user-guide.html#expiry-and-refresh): duration or point in time, variable expiry per entry, delta calculations
 * CacheLoader with blocking read through, see [User Guide - Loading and Read Through](https://cache2k.org/docs/1.0/user-guide.html#loading-read-through)
 * CacheWriter
 * [Event listeners](https://cache2k.org/docs/1.0/user-guide.html#event-listeners)
 * [Refresh ahead](https://cache2k.org/docs/1.0/user-guide.html#refresh-ahead) reduces latency
 * [Low Overhead Statistics](https://cache2k.org/docs/1.0/user-guide.html#statistics) and JMX support
 * [Separate and defined API](https://cache2k.org/docs/1.0/apidocs/cache2k-api/index.html) with stable and concise interface
 * [complete JCache / JSR107 support](https://cache2k.org/docs/1.0/user-guide.html#jcache)
 * [XML based configuration](https://cache2k.org/docs/1.0/user-guide.html#xml-configuration), to separate cache tuning from logic

## More...

For more documentation and latest news, see the [cache2k homepage](https://cache2k.org).
