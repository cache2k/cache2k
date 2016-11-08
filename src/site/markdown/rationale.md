Don't make the same mistake twice. Let's write down some design decisions.

## Why get(? extends K) instead of get(Object)?

Java's Map interface as well as Guava use `get(Object)`. This allows for every key type to be passed
in. Details about the background can be found here: 
[What are the reasons why Map.get(Object key) is not (fully) generic](http://stackoverflow.com/questions/857420/what-are-the-reasons-why-map-getobject-key-is-not-fully-generic)

JSR107 defines it as `get(? extends K)`. cache2k also uses `? extends K` for every accessor.
This enforces the normal usage of the cache, it is accessed with the declared key type.

Further benefits: If a specialized version for the key type is provided, there is no additional
cast from `Object` needed. For a key type that also has a primitive type representation it is
also possible to overload the method. With `get(Object)` no overload is possible.

## Why not `boolean remove(? extends K)` but `void remove(? extends K)`?

There should be a dedicated set of methods that do not expose or mutate the cache state
 without notifying the cache loader or writer. 'Normal' methods that interact with the
 loader and the writer and expose the cache state lead to misinterpretations: The `boolean`
 means that the value isn't existing in the cache, but it does not mean that the value
 is not existing at the system of record.
 
To provide the functionality the method `containsAndRemove(? extends K)` is available.

Not returning the `boolean`  can also be implemented more efficient.
 
In case of a read through or cache through configuration the reduced interfaces
`KeyValueSource` or `KeyValueStore` can be used to restrict to a method set that
works transparently.

## No `LoadingCache`?

TODO

## Why `peek` and not `getIfPresent`?

TODO

## Builder patterns

An early version of cache2k used dedicated methods on the builder object to introduce
 a new section:

````
   CacheBuilder.newBuilder()
      .eternal(true)
      .refreshAhead(false)
      .persistence()
        .entryCapacity(_storage)
        .passivation(_passivation)
      .build()  
````

This pattern is used, for example in Infinispan as well. Problems: The section builder objects
need to define build() and also all sections. It is not extensible, all sections need to be defined 
upfront. The syntax/indentation does not correlate with the section logic.

TODO

## Time or duration for variable expiry?

In applications there are use cases where items need to renew at a certain point in time.
For example a product should be visible at 9am but not before. Also the HTTP protocol defines
the 'expires' header, which is a point in time.

Other caches use a duration or time span to control the expiry of an object. The duration is
an ambiguous concept, because start time as a reference is not defined most of the time. 
Does the time span start after the loader has finished or before the loader was called?

cache2k uses a point in time for variable expiry control, a long value representing the milliseconds 
since the epoch. The used time reference is `System.currentTimeMillis()`. This has the advantage
that the policy can return a distinct point in time if this is requested by the application or
can calculate a reasonable point in time based on a duration configuration with a time reference
of choice (current time or load time). If it is required that the value is not returned by the
cache when the point in time is reached, then the parameter `sharpExpiry` can be switched on.

To sum it up, cache2k is designed to provide exact timing aligned with the wall clock that is provided by Java 
`System.currentTimeMillis()`. 

This comes with a disadvantage, too: The system time has not the guarantee of being continuously ascending, 
since it may be set to another value. So, it is important that system time is properly synchronized and 
not making huge changes. 
 
## Events

The created listener is defined as:

````
public interface CacheEntryCreatedListener<K, V> ... {

  void onEntryCreated(Cache<K,V> cache, CacheEntry<K,V> entry);

}
````

The alternative and more common concept would define `onEntryCreated(CreatedEvent ev)`. This is clearly the
 better choice when applying good API design practices and has two advantages: The `CreatedEvent` can be
 extended later with new properties without breaking existing clients; the event can be passed on e.g. via
 a queue more easily.
 
On the other hand creating a new object for each event is additional overhead and adds one more indirection 
complicating clients. The choice was made against good API design practices, to keep it more simple.

## No Arrays as Values

Using arrays as values is not supported, because the cache operations testing for equality, 
like `Cache.replaceIfEquals` will not work as desired on arrays. 

In general, supporting arrays is feasible, some cache implementations allow arrays and solve the problem of equality by
 making the equality a customization option. The only argument for arrays is to save the extra object instance.
Supporting arrays, would make the cache code base more complex with little benefit.

To support caching of arrays of primitive types, some standard wrapper object should be provided.

Maybe we should drop the check of array value type in the configuration, since it will never
completely protect against the usage of arrays. In case the cache is by-reference only and
 no operations are used that check for equality, arrays would work as values.
