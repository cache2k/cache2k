


# Bypassing with blocking

    CacheBuilder.newCache(String.class, String.class)
      .expiry(0)
      .source( ... )
      .build();

Configures a cache that does no caching at all. However, the cache is not completely
useless. The cache blocks out parallel requests on the same cache key. After a
fetch from the source is finished, all queued up threads will get the identical
result. After servicing all requests for the identical key, the cache will forget
about it.

# Expire and check for freshness

After an entry expires the data may be kept in the cache. The semantics of expiry
only means that the data is not fresh any more and not allowed to be returned.

    CacheBuilder.newCache(String.class, String.class)
      .expiry(0)
      .keepData()
      .entryCapacity(100)
      .source( ... )
      .build();

This will keep 100 entries in the cache which are immediately expired. Whenever the
entry is requested, the source is called. However the source is called with the data
still in the cache and can check for freshness (e.g. via If-Modified-Since when
HTTP is used to fetch resources).

## Tunable constants

The implementation contains a lot of hopefully wisely chosen constants, e.g. the
 initial hash size. Normally, there is no need to change such a constant, but
 it may be useful for tuning and testing purposes.

The values of constants can be overridden by providing a properties file
 named `org/cache2k/tuning.properties`, or by setting a system property.
 The system property is evaluated last and may therefore override parameters
 supplied by the application package.
 
Here is an example of tuning properties:

    # reduce hash table load factor to avoid collisions
    org.cache2k.impl.BaseCache.Tunable.hashLoadPercent=42
    
    # allow much more threads on our big iron server
    org.cache2k.impl.threading.GlobalPooledExecutor.Tunable.hardLimitThreadCount=2000
    
    # Change the thread factory provider for the global thread pool
    org.cache2k.impl.threading.GlobalPooledExecutor.Tunable.threadFactoryProvider=com.example.MyThreadFactoryProvider    

All property names and semantics can be derived from the implementation Java Doc
 by descend to the subclasses of 
 [TunableConstants](/cache2k-core/apidocs/org/cache2k/impl/util/TunableConstants.html).

Disclaimer: Changing a tunable constant might have no outside effects at all
 or lead to complete failure of the cache. There is normally no check or warning
 if a property is not in the valid range. There is also no regular test, whether
 a tuning property works as expected or not.

Second disclaimer: This mechanism should never be used as an alternative for
configuration parameters for the application. Tunable constants are always
implementation specific to the current release and not exposed via a
stable API. The presence and naming may change from release to release, 
without prior notice.
