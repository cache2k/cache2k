
cache aside


read through

cache through

Object mutation and heap corruption
 
Usually, when a value is stored in the cache and retrieved again an identical object instance is returned.

````
Object anObject = new Object();
cache.put(1, anObject);
assertTrue(anObject == cache.get(1));
````

While this is true for simple cache configurations, it might not be true if objects will go off the Java heap.

The application can make use of the stable references and mutate the cached objects. Here is an example that counts
URLs that where accessed:

````
...
Cache<String, AtomicLong>
cache.get(url).incrementAndGet();
````

While sometime useful, mutating values might lead to undesired effects and is error prone.

In general a compatible application does guarantee the following:
 
 - values and keys are not mutated after handed over to the cache
 - reference identity is not used
 - if reference identity or value mutation is used the application enables the `onlyStoreByReference` option.
   This informs the cache, that objects are kept in the Java heap only.
   
   

