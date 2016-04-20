We track some design decision here.

## Why get(? extends K) instread of get(Object)?

Java's Map interface as well as Guava use `get(Object)`. This allows for every object to be passed
in. Details about the background can be found here: 
[What are the reasons why Map.get(Object key) is not (fully) generic](http://stackoverflow.com/questions/857420/what-are-the-reasons-why-map-getobject-key-is-not-fully-generic)

JSR107 defines it as `get(? extends K)`. cache2k also uses `? extends K` for every accessor.
This enforces the normal usage of the cache, it is accessed with the declared key type.

Further benefits: If a specialized version for the key type is provided, there is no additional
cast from `Object` needed. For a key type that also has a primitive type representation it is
also possible to overload the method. With `get(Object)` no overload is possible.

