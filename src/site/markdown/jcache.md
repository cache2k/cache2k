# JCache

cache2k supports the JCache standard.

## Implementation details

JCache implementations can vary in great detail and still pass 100% of all the TCK tests. 

### Store by value

If store by value is selected (default), keys and values are copied by the cache whenever passed in or out.
To achieve this, the used types need to be serializable as requested by the JCache specification.

### Loader exceptions

The JCache specification does not define semantics in case the loader throws exceptions.
cache2k is able to cache or suppress exceptions, depending on the situation and the configuration.

If an exception is cached, the following behavior can be expected:

  * Accessing the value of the entry, will trigger an exception
  * `Cache.containsKey()` will be true for the respective key
  * `Cache.iterator()` will skip entries that contain exceptions
    
If an exception is suppressed, everything behaves as normal.

In general, the cache2k JCache implementation needs more concise testing for its behavior in the
presence of exceptions.

### Loader exceptions and event listeners

The JCache specification does not define semantics in case the loader throws exceptions. The following semantics
are implemented:

  * No entry is present and loader throws an exception
    * cache2k: Entry created event
    * JCache: no event
  * Entry is present with a valid value and loader throws an exception
    * cache2k: Entry updated event
    * JCache: Entry removed event
  * Entry is present with a previous exception and loader throws an exception
    * cache2k: Entry updated event
    * JCache: no event
  * Entry is present with a previous exception and loader returns a value
    * cache2k: Entry updated event
    * JCache: Entry created event
  * Entry is present with a previous exception and gets removed
    * cache2k: Entry removed event
    * JCache: no event

### Listeners

Asynchronous and synchronous events are supported. Asynchronous events are delivered in a way to achieve highest possible
parallelism while retaining the event order on a single key. Synchronous events are delivered sequentially.

TODO: executor configuration for async events.

### Entry processor

Calling other methods on the cache from inside an entry processor execution (reentrant operation), is not supported.
The entry processor should have no external side effects. To enable asynchronous operations method calls on the 
MutableEntry may yield a RestartException.


