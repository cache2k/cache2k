package org.cache2k;

/*
 * #%L
 * cache2k API
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.spi.Cache2kCoreProvider;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * A cache manager holds a set of caches. Caches within a cache manager share the
 * same class loader and may have a different default configuration. If a cache manager
 * is not specified a default manager is used.
 *
 * <p>Usually there is one cache manager per application which is retrieved by
 * {@link #getInstance()}. Multiple cache managers can be used to separate different caching
 * aspects. This may help with a better organization of the cache configuration and operate with
 * reasonable defaults within each aspect.
 *
 * <p>Cache managers are identified by a unique name. If no name is specified the name
 * {@value STANDARD_DEFAULT_MANAGER_NAME} is used. The default name in use may be
 * changed, see {@link #setDefaultName(String)}.
 *
 * @author Jens Wilke
 */
public abstract class CacheManager implements Closeable {

  /**
   * Name of the default cache manager if not overridden, see {@link #setDefaultName(String)}.
   * @since 1.4
   */
  public static final String STANDARD_DEFAULT_MANAGER_NAME = "default";

  /**
   * The singleton cache provider instance.
   *
   * @since 2
   */
  public static final Cache2kCoreProvider PROVIDER;

  static {
    Iterator<Cache2kCoreProvider> it = ServiceLoader.load(Cache2kCoreProvider.class).iterator();
    if (!it.hasNext()) {
      throw new LinkageError("Cannot resolve cache2k core implementation");
    }
    PROVIDER = it.next();
  }

  /**
   * Name of the default cache manager, which is "{@value STANDARD_DEFAULT_MANAGER_NAME}" by
   * default.
   */
  public static String getDefaultName() {
    return PROVIDER.getDefaultManagerName(PROVIDER.getDefaultClassLoader());
  }

  /**
   * Change the default manager name. The method can only be called once early in application
   * startup, before the default manager instance is requested.
   *
   * <p>It is also possible to set a different default manager name via JNDI context
   * "java:comp/env" and name "org.cache2k.CacheManager.defaultName" or via the XML configuration.
   *
   * <p>The allowed characters in a manager name are identical to the characters in a cache name,
   * this is documented at {@link Cache2kBuilder#name(String)}
   *
   * @see Cache2kBuilder#name(String)
   */
  public static void setDefaultName(String managerName) {
    PROVIDER.setDefaultManagerName(PROVIDER.getDefaultClassLoader(), managerName);
  }

  /**
   * Get the default cache manager for the default class loader. The default class loader
   * is the class loader used to load the cache2k implementation classes.
   *
   * <p>The name of default cache manager is "{@value STANDARD_DEFAULT_MANAGER_NAME}".
   * This may be changed, by {@link #setDefaultName(String)}.
   */
  public static CacheManager getInstance() {
    ClassLoader defaultClassLoader = PROVIDER.getDefaultClassLoader();
    return PROVIDER.getManager(
      defaultClassLoader, PROVIDER.getDefaultManagerName(defaultClassLoader));
  }

  /**
   * Get the default cache manager for the specified class loader.
   */
  public static CacheManager getInstance(ClassLoader cl) {
    return PROVIDER.getManager(cl, PROVIDER.getDefaultManagerName(cl));
  }

  /**
   * Retrieve a cache manager with the specified name. If not existing, a manager with that name
   * is created. The default class loader is used. The default class loader
   * is the class loader used to load the cache2k implementation classes.
   *
   * <p>The allowed characters in a manager name are identical to the characters in a cache name,
   * this is documented at {@link Cache2kBuilder#name(String)}
   *
   * @see Cache2kBuilder#name(String)
   */
  public static CacheManager getInstance(String managerName) {
    return PROVIDER.getManager(PROVIDER.getDefaultClassLoader(), managerName);
  }

  /**
   * Retrieve a cache manager with the specified name using the specified classloader.
   * If not existing, a manager with that name is created. Different cache managers are
   * created for different class loaders. Manager names should be unique within one VM instance.
   *
   * <p>The allowed characters in a manager name are identical to the characters in a cache name,
   * this is documented at {@link Cache2kBuilder#name(String)}
   *
   * @see Cache2kBuilder#name(String)
   */
  public static CacheManager getInstance(ClassLoader cl, String managerName) {
    return PROVIDER.getManager(cl, managerName);
  }

  /**
   * Close all cache managers.
   */
  public static void closeAll() {
    PROVIDER.close();
  }

  /**
   * Close all cache manager associated with this class loader.
   */
  public static void closeAll(ClassLoader cl) {
    PROVIDER.close(cl);
  }

  /**
   * Close the named cache manager.
   */
  public static void close(ClassLoader cl, String name) {
    PROVIDER.close(cl, name);
  }

  /**
   * True if this is the default manager of the application, returned by {@link #getInstance()}
   */
  public abstract boolean isDefaultManager();

  /**
   * The name to uniquely identify the manager within a VM instance.
   *
   * @see CacheManager#getInstance(String)
   */
  public abstract String getName();

  /**
   * Returns all open caches associated with this cache manager. A cache returned by the iteration
   * was created via {@link #createCache(Cache2kConfig)} or
   * {@link Cache2kBuilder#build()} and is not closed yet.
   */
  public abstract Iterable<Cache> getActiveCaches();

  /**
   * Returns a list of caches that are found in the XML based configuration.
   */
  public abstract Iterable<String> getConfiguredCacheNames();

  /**
   * Return a known cache that must be created before via the {@link Cache2kBuilder}
   * or {@link #createCache(Cache2kConfig)}
   */
  public abstract <K, V> Cache<K, V> getCache(String name);

  /**
   * Create a new cache from the configuration. The recommended way is to use the
   * {@link Cache2kBuilder} to create a new cache. This method is identical to and a shorthand to:
   *
   * <pre>{@code
   *    CacheManager manager = ...
   *    Cache2kConfiguration<K, V> config = ...
   *    Cache<K, V> cache = Cache2kBuilder.of(config).manager(manager).build();
   * }</pre>
   *
   */
  public abstract <K, V> Cache<K, V> createCache(Cache2kConfig<K, V> cfg);

  /** Clear all currently active caches in this cache manager */
  public abstract void clear();

  /**
   * Free all resources from managed caches. Same as calling all {@link org.cache2k.Cache#close()}
   * methods. A closed manager cannot be used to create new caches. A new {@code CacheManager} with
   * the same name may be requested via {@link #getInstance(String)}. Multiple calls to close have
   * no effect.
   */
  public abstract void close();

  /**
   * Returns true if this cache manager was closed.
   *
   * @see #close()
   */
  public abstract boolean isClosed();

  /**
   * Properties for the cache manager, never null. By default the properties are empty.
   * Cache clients may store arbitrary information.
   */
  public abstract Properties getProperties();

  /**
   * Class loader this manager is using to load additional classes, resources or configuration.
   */
  public abstract ClassLoader getClassLoader();

}
