package org.cache2k.extra.spring;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.Cache2kConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jens Wilke
 */
public class Cache2kCacheManager implements CacheManager {

  private final org.cache2k.CacheManager manager;

  private final Map<String, Cache2kCache> name2cache = new ConcurrentHashMap<>();

  private final Set<String> configuredCacheNames = new HashSet<>();

  /**
   * Construct a spring cache manager, using the default cache2k cache manager instance.
   *
   * @see org.cache2k.CacheManager
   * @see org.cache2k.CacheManager#getInstance()
   */
  public Cache2kCacheManager() {
    this(org.cache2k.CacheManager.getInstance());
  }

  /**
   * Construct a spring cache manager, using a custom cache2k cache manager. This
   * allows for the use of a different manager name and classloader.
   *
   * @see org.cache2k.CacheManager
   * @see org.cache2k.CacheManager#getInstance(String)
   * @see org.cache2k.CacheManager#getInstance(ClassLoader)
   * @see org.cache2k.CacheManager#getInstance(ClassLoader, String)
   */
  public Cache2kCacheManager(org.cache2k.CacheManager manager) {
    this.manager = manager;
    manager.getConfiguredCacheNames().forEach(configuredCacheNames::add);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Cache2kCache getCache(final String name) {
    return name2cache.computeIfAbsent(name, n ->
      buildAndWrap(configureCache(Cache2kBuilder.forUnknownTypes().manager(manager).name(n))));
  }

  /**
   * Add a cache to this cache manager that is configured via the {@link Cache2kBuilder}.
   * The configuration parameters {@link Cache2kBuilder#exceptionPropagator}
   * and {@link Cache2kBuilder#permitNullValues} are managed by this class. The
   * {@link Cache2kBuilder#manager} must be identical to the one wrapped by this class,
   * so it is best to create the builder with
   * {@code Cache2kBuilder.of(...).manager(manager,getNativeManager())}
   *
   * <p>This method can be used in case a programmatic configuration of a cache manager
   * is preferred.
   *
   * @param builder builder with configuration, with a name and the identical cache manager
   * @return the wrapped spring cache
   * @throws IllegalArgumentException if cache is already created
   */
  public Cache2kCache addCache(final Cache2kBuilder builder) {
    String name = builder.toConfiguration().getName();
    Assert.notNull(name, "Name must be set via Cache2kBuilder.name()");
    Assert.isTrue(builder.getManager() == manager,
      "Manager must be identical in builder. Do Cache2kBuilder.manager(manager.getNativeManager()");
    return name2cache.compute(name, (name2, existingCache) -> {
      Assert.isNull(existingCache, "Cache is not yet configured");
      return buildAndWrap(configureCache(builder));
    });
  }

  /**
   * Add a cache to this cache manager that is configured via the {@link Cache2kConfiguration}.
   *
   * <p>This method can be used in case a programmatic configuration of a cache manager
   * is preferred.
   *
   * @param cfg the cache configuration object
   * @return the wrapped spring cache
   * @throws IllegalArgumentException if cache is already created
   */
  public Cache2kCache addCache(final Cache2kConfiguration cfg) {
    return addCache(Cache2kBuilder.of(cfg).manager(manager));
  }

  /**
   * Get a list of known caches. Depending on the configuration caches may be created
   * dynamically without providing a configuration for a specific cache name. Because of this
   * combine the known names from configuration and activated caches.
   */
  @Override
  public Collection<String> getCacheNames() {
    Set<String> cacheNames = new HashSet<>();
    for (org.cache2k.Cache<?,?> cache : manager.getActiveCaches()) {
      cacheNames.add(cache.getName());
    }
    cacheNames.addAll(configuredCacheNames);
    return Collections.unmodifiableSet(cacheNames);
  }

  /**
   * Expose the native cache manager.
   */
  public org.cache2k.CacheManager getNativeCacheManager() {
    return manager;
  }

  /**
   * Expose the map of known wrapped caches.
   */
  public Map<String, Cache2kCache> getCacheMap() {
    return name2cache;
  }

  public static final Callable<Object> DUMMY_CALLABLE = new Callable<Object>() {
    @Override
    public Object call() {
      return null;
    }
    public String toString() {
      return "Callable(DUMMY)";
    }
  };

  public static Cache2kBuilder<Object,Object> configureCache(Cache2kBuilder<Object, Object> builder) {
    return builder
      .permitNullValues(true)
      .exceptionPropagator(
        (key, exceptionInformation) ->
          new Cache.ValueRetrievalException(key, DUMMY_CALLABLE, exceptionInformation.getException()));
  }

  public static Cache2kCache buildAndWrap(Cache2kBuilder<Object, Object> builder) {
    org.cache2k.Cache<Object, Object> nativeCache = builder.build();
    Cache2kConfiguration<?,?> cfg = builder.toConfiguration();
    boolean loaderPresent = cfg.getLoader() != null || cfg.getAdvancedLoader() != null;
    return loaderPresent ? new LoadingCache2kCache(nativeCache) : new Cache2kCache(nativeCache);
  }

}
