package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.annotation.NonNull;
import org.cache2k.operation.CacheControl;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.CacheManager;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

/**
 * A Spring cache manager that manages cache2k caches. The manager delegates to the cache2k
 * native {@link org.cache2k.CacheManager}. The available caches can be
 * configured programmatically in a Spring configuration class via the use
 * of {@link #addCaches(Function[])}  or via the cache2k XML configuration.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#spring">
 *   Spring Framework - cache2k User Guide</a>
 */
public class SpringCache2kCacheManager implements CacheManager, DisposableBean {

  /**
   * Default name used for the cache2k cache manager controlled by the spring cache
   * manager.
   */
  public static final String DEFAULT_SPRING_CACHE_MANAGER_NAME = "springDefault";

  private final org.cache2k.CacheManager manager;

  private final Map<String, SpringCache2kCache> name2cache = new ConcurrentHashMap<>();

  private final Set<String> configuredCacheNames = new CopyOnWriteArraySet<>();

  private boolean allowUnknownCache = true;

  private Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>> defaultSetup = b -> b;

  /**
   * Construct a spring cache manager, using the cache2k cache manager with
   * the name {@value DEFAULT_SPRING_CACHE_MANAGER_NAME}. It is recommended
   * that the spring cache manager uses a cache2k cache manager exclusively and
   * all caches are created only via the spring cache manager.
   * (Behavior since cache2k version 1.4)
   *
   * @see org.cache2k.CacheManager
   */
  public SpringCache2kCacheManager() {
    this(DEFAULT_SPRING_CACHE_MANAGER_NAME);
  }

  /**
   * Construct a spring cache manager, using the cache2k cache manager instance
   * with the specified name.
   *
   * @see org.cache2k.CacheManager
   * @see org.cache2k.CacheManager#getInstance()
   *
   */
  public SpringCache2kCacheManager(String name) {
    this(org.cache2k.CacheManager.getInstance(name));
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
  public SpringCache2kCacheManager(org.cache2k.CacheManager manager) {
    this.manager = manager;
    manager.getConfiguredCacheNames().forEach(configuredCacheNames::add);
  }

  /**
   * Returns an existing cache or creates and wraps a new cache if the creation
   * of unknown caches is enabled. When a new cache is created which was not added by
   * {@link #addCaches} the default setup from {@link #defaultSetup} is applied.
   */
  @Override
  public @NonNull SpringCache2kCache getCache(String name) {
    return name2cache.computeIfAbsent(name, n -> {
        if (!allowUnknownCache && !configuredCacheNames.contains(n)) {
          return null;
        }
        return buildAndWrap(defaultSetup.apply(getDefaultBuilder()).name(n));
      });
  }

  /**
   * Function providing default settings for caches added to the manager via {@link #addCaches}
   * and for dynamically created caches (not configured explicitly) that are retrieved
   * via {@link #getCache(String)}. The defaults must be set first, before caches are added
   * or retrieved.
   *
   * @throws IllegalStateException if caches were already added
   */
  public SpringCache2kCacheManager defaultSetup(
    Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>> f) {
    if (!name2cache.isEmpty()) {
      throw new IllegalStateException("Defaults may only be set before the first cache is added");
    }
    defaultSetup = f;
    return this;
  }

  /**
   * @see #addCache(Function) 
   */
  @SafeVarargs
  public final SpringCache2kCacheManager addCaches(
    Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>>... fs) {
    for (Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>> f : fs) {
      addCache(f);
    }
    return this;
  }

  /**
   * Adds a cache to this cache manager that may be configured via the {@link Cache2kBuilder}.
   * This method can be used in case a programmatic configuration of a cache manager is preferred.
   *
   * <p>The provided builder is already seeded with the effective cache2k cache manager and default configuration.
   *
   * @throws IllegalStateException if cache is already created
   */
  public SpringCache2kCacheManager addCache(Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>> f) {
    buildAndAddCache(f.apply(defaultSetup.apply(getDefaultBuilder())));
    return this;
  }

  /**
   * Adds a cache to this cache manager that may be configured via the {@link Cache2kBuilder}.
   *
   * @param name name of the cache to add
   * @param f customizer function for the builder provided with the defaults
   * @return the customized builder
   */
  public SpringCache2kCacheManager addCache(String name, Function<Cache2kBuilder<?, ?>, Cache2kBuilder<?, ?>> f) {
    buildAndAddCache(f.apply(defaultSetup.apply(getDefaultBuilder()).name(name)));
    return this;
  }

  private void buildAndAddCache(Cache2kBuilder<?, ?> builder) {
    String name = builder.config().getName();
    Assert.notNull(name, "Name must be set via Cache2kBuilder.name()");
    Assert.isTrue(builder.getManager() == manager,
      "Manager must be identical in builder.");
    configuredCacheNames.add(name);
    name2cache.compute(name, (name2, existingCache) -> {
      if (existingCache != null) {
        throw new IllegalStateException("Cache already configured");
      }
      return buildAndWrap(builder);
    });
  }

  /**
   * @see #setDefaultCacheNames(Collection)
   */
  public SpringCache2kCacheManager setDefaultCacheNames(String... names) {
    return setDefaultCacheNames(Arrays.asList(names));
  }

  /**
   * Initialize the specified caches with defaults and disable
   * dynamic creation of caches by setting {@link #setAllowUnknownCache(boolean)} to {@code false}
   */
  public SpringCache2kCacheManager setDefaultCacheNames(Collection<String> names) {
    for (String name : names) {
      addCache(b -> b.name(name));
    }
    allowUnknownCache = false;
    return this;
  }

  /**
   * Get a list of known caches. Depending on the configuration, caches may be created
   * dynamically with a default configuration. Because of this
   * combine the known names from configuration and activated caches.
   */
  @Override
  public Collection<String> getCacheNames() {
    Set<String> cacheNames = new HashSet<>();
    for (org.cache2k.Cache<?, ?> cache : manager.getActiveCaches()) {
      cacheNames.add(cache.getName());
    }
    cacheNames.addAll(configuredCacheNames);
    return Collections.unmodifiableSet(cacheNames);
  }

  /**
   * List of configured cache names. These are caches configured in cache2k via XML and
   * caches added to this cache manager with programmatic configuration {@link #buildAndAddCache}.
   * Used for testing purposes to determine whether a cache was added dynamically.
   */
  public Collection<String> getConfiguredCacheNames() {
    return Collections.unmodifiableSet(configuredCacheNames);
  }

  /**
   * Expose the native cache manager.
   */
  public org.cache2k.CacheManager getNativeCacheManager() {
    return manager;
  }

  /**
   * Expose the map of created and wrapped caches.
   */
  public Map<String, SpringCache2kCache> getCacheMap() {
    return Collections.unmodifiableMap(name2cache);
  }

  public boolean isAllowUnknownCache() {
    return allowUnknownCache;
  }

  /**
   * If {@code false}, the cache manager only manages a cache if added via {@link #addCaches},
   * of {@link #setDefaultCacheNames(String...)} or enlisted in the cache2k XML configuration.
   * Setting this to {@code true} will create a cache with a default configuration if the requested
   * cache name is not known. The default is {@code true}.
   *
   * <p>Other Spring cache managers use the notion of 'dynamic' cache creation for the same
   * concept.
   */
  public void setAllowUnknownCache(boolean v) {
    allowUnknownCache = v;
  }

  /**
   * Cache2k builder with reasonable default for Spring. Permit nulls because, "Spring caches"
   * allow storage of null values.
   */
  private Cache2kBuilder<?, ?> getDefaultBuilder() {
    Cache2kBuilder<?, ?> b = Cache2kBuilder.forUnknownTypes();
    b.manager(manager);
    springDefaults(b);
    return b;
  }

  @SuppressWarnings("unchecked")
  private SpringCache2kCache buildAndWrap(Cache2kBuilder<?, ?> builder) {
    org.cache2k.Cache<Object, Object> nativeCache = (Cache<Object, Object>)  builder.build();
    return CacheControl.of(nativeCache).isLoaderPresent() ?
      new SpringLoadingCache2kCache(nativeCache) : new SpringCache2kCache(nativeCache);
  }

  @Override
  public void destroy() {
    manager.close();
    name2cache.clear();
  }

  /**
   * Defaults for cache2k caches within Spring context.
   * Spring caches allow the storage of null values by default.
   */
  public static void springDefaults(Cache2kBuilder<?, ?> builder) {
    builder.permitNullValues(true);
  }

}
