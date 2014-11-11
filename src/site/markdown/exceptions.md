Notes on exception handling, incomplete yet.

# Exception support

Exceptions from the cache source, in a read through configuration, are cached and 
rethrown wrapped into a `PropagatedCacheException`. An exception will be cached.
For a cached exception the cache throws the `PropagatedCacheException` each time
the resource is requested.

## Default semantics 

The defaults semantics are chosen based on the idea that an exception is
something which happens only occasional or never. 

Expiry: If an expiry time is configured, the exception will expire more early
in the hope that the exception was only temporarily and will go away on the
next try requesting the same resource. The default exception expiry is one
tenth of the value configured value expiry.

Suppressed exceptions: If there is still a value which was fetched previously
from the cache source, an exception will be suppressed and the old value
will be kept. This means that in the event of an exception possible expired
values will be returned by the cache.

## Configuration options





Caching the exception means that the cache source is not asked for the resource
 the cache source is requested 

Exceptions are inherent to Java programs, so cache2k has exception support build in.

## Exception types

### Temporary exceptions

