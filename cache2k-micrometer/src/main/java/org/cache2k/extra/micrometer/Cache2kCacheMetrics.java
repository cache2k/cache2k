package org.cache2k.extra.micrometer;

/*
 * #%L
 * cache2k micrometer monitoring support
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import org.cache2k.Cache;
import org.cache2k.configuration.CacheType;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer support for cache2k
 *
 * @author Jens Wilke
 */
public class Cache2kCacheMetrics extends CacheMeterBinder {

  private final InternalCache<?, ?> cache;

  /**
   * Creates a new {@link Cache2kCacheMetrics} instance.
   *
   * @param cache     The cache to be instrumented.
   * @param tags      tags to apply to all recorded metrics.
   */
  public Cache2kCacheMetrics(Cache<?, ?> cache, Iterable<Tag> tags) {
    super(cache, cache.getName(), Tags.concat(tags, extendedTags(cache)));
    this.cache = cache.requestInterface(InternalCache.class);
  }

  /**
   * Record metrics for a cache2k cache.
   *
   * @param registry  The registry to bind metrics to.
   * @param cache     The cache to instrument.
   * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments
   *                  representing key/value pairs of tags.
   * @param <C>       The cache type.
   * @return the cache as passed in.
   */
  public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String... tags) {
    return monitor(registry, cache, Tags.of(tags));
  }

  /**
   * Record metrics for a cache2k cache.
   *
   * @param registry  The registry to bind metrics to.
   * @param cache     The cache to instrument.
   * @param tags      Tags to apply to all recorded metrics.
   * @param <C>       The cache type.
   * @return the cache as passed in.
   */
  public static <C extends Cache> C monitor(MeterRegistry registry, C cache, Iterable<Tag> tags) {
    new Cache2kCacheMetrics(cache, tags).bindTo(registry);
    return cache;
  }

  @Override
  protected Long size() {
    return cache.getInfo().getSize();
  }

  @Override
  protected long hitCount() {
    InternalCacheInfo info = cache.getInfo();
    return info.getGetCount() - info.getMissCount();
  }

  @Override
  protected Long missCount() {
    return cache.getInfo().getMissCount();
  }

  /**
   * Sum up everything which removes an entry no matter the cause.
   * This is probably most similar to the behavior of other caches.
   *
   * <p>TODO: have separate metric for expired entries
   */
  @Override
  protected Long evictionCount() {
    InternalCacheInfo inf = cache.getInfo();
    return inf.getEvictedCount() + inf.getExpiredCount() + inf.getRemoveCount();
  }

  @Override
  protected long putCount() {
    return cache.getInfo().getPutCount();
  }

  /**
   * Returns additional metrics similar to Caffeine and Guava.
   *
   * <p>TODO: loads/refresh, evicted/expired
   */
  @Override
  protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
    if (cache.isWeigherPresent()) {
      Gauge.builder("cache.currentWeight", cache,
        c -> c.getInfo().getCurrentWeight())
        .tags(getTagsWithCacheName())
        .description("The sum of weights of all cached entries.")
        .register(registry);
    }

    if (cache.isLoaderPresent()) {
      TimeGauge.builder("cache.load.duration", cache, TimeUnit.NANOSECONDS,
        c -> ((double) c.getInfo().getLoadMillis()) * 1000)
        .tags(getTagsWithCacheName())
        .description("The time the cache has spent loading new values")
        .register(registry);

      FunctionCounter.builder("cache.load", cache,
        c -> { InternalCacheInfo info = c.getInfo(); return info.getLoadCount(); })
        .tags(getTagsWithCacheName()).tags("result", "success")
        .description(
          "The number of times cache lookup methods have successfully loaded a new value")
        .register(registry);

      FunctionCounter.builder("cache.load", cache, c -> c.getInfo().getLoadExceptionCount())
        .tags(getTagsWithCacheName()).tags("result", "failure")
        .description(
          "The number of times cache lookup methods threw an exception while loading a new value")
        .register(registry);
    }

  }

  /**
   * Additional tags. The base class adds tag {@code cache} for the cache name.
   * The Spring cache metrics autoconfiguration would set {@code cacheManager} to the bean name
   * of the cache manager. To avoid confusion, the name of the cache manager of cache2k is
   * exported as tag {@code cache2kCacheManager}. cache2k does not use the Spring metrics
   * autoconfiguration, since it adds the cache metrics to micro meter by itself.
   */
  private static Iterable<Tag> extendedTags(Cache<?, ?> cache) {
    InternalCache internalCache = cache.requestInterface(InternalCache.class);
    InternalCacheInfo info = internalCache.getInfo();
    return Tags.of(
      "cache2kCacheManager", internalCache.getCacheManager().getName(),
      "keyType=", internalCache.getKeyType().toString()
        .substring(CacheType.DESCRIPTOR_TO_STRING_PREFIX.length()),
      "valueType=", internalCache.getKeyType().toString()
        .substring(CacheType.DESCRIPTOR_TO_STRING_PREFIX.length())
    );
  }

}
