# Features

TODO...

## Background refresh / refresh ahead

Oracle lingo "refresh-ahead caching", see:
http://docs.oracle.com/cd/E15357_01/coh.360/e15723/cache_rtwtwbra.htm#CHDGFFIA

## Null value support

A null value is cached, just as any other value. A null key is not allowed.

Also supporting nulls is useful, it also may add some ambiguity or unexpected
behaviour. E.g.

    assertNull(cache.get("somekey"));

This means either that there is nothing mapped in the cache for that key, or that
a null is mapped. So each developer should be aware whether nulls will be
present in the cache or not.



## Exception support


## Nice statistics

....

## Resistance against mutated key objects

cache2k uses object references directly for performance reasons. Bad things may happen
when cache keys will be mutated. The worst is, that a cache entry cannot be removed
by the cache itself since the same but mutated key object will compute a different hashcode as
by the time it was inserted. The cache2k implementation is resistant against mutated key and
will print out a warning message, when it detects them.

