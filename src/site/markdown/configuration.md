


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



