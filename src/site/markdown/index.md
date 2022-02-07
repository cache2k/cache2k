cache2k focuses on providing a well engineered in-memory object cache implementation for
Java applications. 

[![GitHub Stars](https://x.h7e.eu/badges/xz/q/github/starGazers/gh-stargazers/cache2k/cache2k)](https://github.com/cache2k/cache2k/stargazers)
[![License](https://x.h7e.eu/badges/xz/txt/license/apache)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Stack Overflow](https://x.h7e.eu/badges/xz/txt/stackoverflow/cache2k)](https://stackoverflow.com/questions/tagged/cache2k)
![Maven Central](https://x.h7e.eu/badges/xz/q/maven/latestVersion/maven-central/org.cache2k/cache2k-api)

````java
  Cache<String,String> cache = new Cache2kBuilder<String, String>() {}
    .expireAfterWrite(5, TimeUnit.MINUTES)    // expire/refresh after 5 minutes
    .setupWith(UniversalResiliencePolicy::enable, b -> b // enable resilience policy
      .resilienceDuration(30, TimeUnit.SECONDS)          // cope with at most 30 seconds
                                                         // outage before propagating 
                                                         // exceptions
    )
    .refreshAhead(true)                       // keep fresh when expiring
    .loader(this::expensiveOperation)         // auto populating function
    .build();
````

For a detailed introduction continue with [Getting Started](docs/latest/user-guide.html#getting-started).

## Features at a glance

 * Small jar file (less than 400k) with no external dependencies
 * Fastest access times, due to non blocking and wait free access of cached values, see [Benchmarks](benchmarks.html)
 * Pure Java code, no use of `sun.misc.Unsafe`
 * Thread safe, with a complete set of [atomic operations](docs/latest/user-guide.html#atomic-operations)
 * [Resilience and smart exception handling](docs/latest/user-guide.html#resilience) 
 * `Null` value support, see [User Guide - Null Values](docs/latest/user-guide.html#null-values)
 * Automatic [Expiry and Refresh](docs/latest/user-guide.html#expiry-and-refresh): duration or point in time, variable expiry per entry, delta calculations
 * CacheLoader with blocking read through, see [User Guide - Loading and Read Through](docs/latest/user-guide.html#loading-read-through)
 * CacheWriter
 * [Event listeners](docs/latest/user-guide.html#event-listeners)
 * [Refresh ahead](docs/latest/user-guide.html#refresh-ahead) reduces latency
 * [Low Overhead Statistics](docs/latest/user-guide.html#statistics) and JMX support
 * [Separate API](docs/latest/apidocs/cache2k-api/index.html) with stable and concise interface
 * [complete JCache / JSR107 support, complatible to JCache Specification 1.1](docs/latest/user-guide.html#jcache)
 * [XML based configuration](docs/latest/user-guide.html#xml-configuration), to separate cache tuning from logic

## Integrations

 * [Spring Framework](docs/latest/user-guide.html#spring)
 * [Scala Cache](https://github.com/cb372/scalacache)
 * Datanucleus (via JCache)
 * Hibernate (via JCache)
 * .... and probably more, please raise an issue and get it listed! 

## News

  * **Version 2.6.1.Final, 2022-02-07**: Minor fixes, see [Version 2.6.1.Final release notes](2/6.1.Final.html)
  * **Version 2.6.0.Final, 2022-02-01**: Cache disabling, idle scan (time to idle expiry alternative), Android CI tests, see [Version 2.6.0.Final release notes](2/6.0.Final.html)
  * **Version 2.4.1.Final, 2021-10-28**: Minor fixes, see [Version 2.4.1.Final release notes](2/4.1.Final.html)
  * **Version 2.4.0.Final, 2021-10-25**: Performance improvements for big caches, see [Version 2.4.0.Final release notes](2/4.0.Final.html)
  * **Benchmarks update, 2021-10-25**: [Updated benchmarks](benchmarks.html) comparing with Caffeine 3.0.4, and EHCache3 3.9.6
  * **Version 2.2.0.Final, 2021-08-07**: Bulk loader support, see [Version 2.2.0.Final release notes](2/2.0.Final.html)
  * **Version 2.0.0.Final, 2020-12-15**: Major release, improved Java 8 support, API cleanups, see [Version 2.0.0.Final release notes](2/0.0.Final.html)
  * **Version 1.6.0.Final, 2020-10-09**: Improved timer code for expiry, fixes and cleanup, see [Version 1.6.0.Final release notes](1/6.0.Final.html)
  * **Version 1.4.1.Final, 2020-10-02**: Critical bug fix, see [Version 1.4.1.Final release notes](1/4.1.Final.html)
  * **Version 1.4.0.Final, 2020-09-12**: Eviction listener, GraalVM support, async loading, see [Version 1.4.0.Final release notes](1/4.0.Final.html)
  * **Version 1.2.4.Final, 2019-09-04**: Service release for stable version, See [Version 1.2.4.Final release notes](1/2.4.Final.html)

## Feedback and Contribute

Please use the [GitHub issue tracker](https://github.com/cache2k/cache2k) for bugs and wishes you step upon. We also monitor the stackoverflow.com tag
**cache2k** for general usage questions.

See also the [Contributor Guide](https://github.com/cache2k/cache2k/blob/master/CONTRIBUTING.md).

## License

The code is released under Apache license. 
