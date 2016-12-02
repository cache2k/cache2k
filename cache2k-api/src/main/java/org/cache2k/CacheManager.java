package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.spi.Cache2kExtensionProvider;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.io.Closeable;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * A cache manager holds a set of caches. Caches within a cache manager share the
 * same class loader and may have a different default configuration. If a cache manager
 * is not specified a default manager is used.
 *
 * <p>Cache managers are identified by a unique name. If no name is specified the name
 * {@code "default"} is used. The default name in use may be changed, see {@link #setDefaultName(String)}.
 *
 * @author Jens Wilke
 */
public abstract class CacheManager implements Closeable {

  static protected final Cache2kCoreProvider PROVIDER;

  static {
    PROVIDER = SingleProviderResolver.resolveMandatory(Cache2kCoreProvider.class);
    ServiceLoader<Cache2kExtensionProvider> _loader =
      ServiceLoader.load(Cache2kExtensionProvider.class, CacheManager.class.getClassLoader());
    for (Cache2kExtensionProvider p : _loader) {
      p.register();
    }
  }

  /**
   * Name of the default cache manager, which is "default" by default.
   */
  public static String getDefaultName() {
    return PROVIDER.getDefaultManagerName(PROVIDER.getDefaultClassLoader());
  }

  /**
   * Change the default manager name. The method can only be called once early in application startup,
   * before the default manager instance is requested.
   *
   * <p>It is also possible to set a different default manager name via JNDI context
   * "java:comp/env" and name "org.cache2k.CacheManager.defaultName" or via the XML configuration.
   *
   * <p>The allowed characters in a manager name are identical to the characters in a cache name, this is
   * documented at {@link Cache2kBuilder#name(String)}
   *
   * @see Cache2kBuilder#name(String)
   */
  public static void setDefaultName(String managerName) {
    PROVIDER.setDefaultManagerName(PROVIDER.getDefaultClassLoader(), managerName);
  }

  /**
   * Get the default cache manager for the default class loader. The default class loader
   * is the class loader used to load the cache2k implementation classes.
   */
  public static CacheManager getInstance() {
    ClassLoader _defaultClassLoader = PROVIDER.getDefaultClassLoader();
    return PROVIDER.getManager(_defaultClassLoader, PROVIDER.getDefaultManagerName(_defaultClassLoader));
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
   * <p>The allowed characters in a manager name are identical to the characters in a cache name, this is
   * documented at {@link Cache2kBuilder#name(String)}
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
   * <p>The allowed characters in a manager name are identical to the characters in a cache name, this is
   * documented at {@link Cache2kBuilder#name(String)}
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
   * Returns all caches created in this cache manager instance.
   */
  public abstract Iterable<Cache> getActiveCaches();

  /**
   * Return a known cache that must be created before via the {@link Cache2kBuilder}
   * or {@link #createCache(Cache2kConfiguration)}
   */
  public abstract <K,V> Cache<K,V> getCache(String name);

  /**
   * Create a new cache from the configuration. The recommended way is to use the {@link Cache2kBuilder}
   * to create a new cache. This method is identical to and a shorthand to:
   *
   * <pre>{@code
   *    CacheManager manager = ...
   *    Cache2kConfiguration<K,V> config = ...
   *    Cache<K,V> cache = Cache2kBuilder.of(config).manager(manager).build();
   * }</pre>
   *
   */
  public abstract <K,V> Cache<K,V> createCache(Cache2kConfiguration<K, V> cfg);

  /** Clear all currently active caches in this cache manager */
  public abstract void clear();

  /**
   * @deprecated Use {@link #close()}
   */
  public abstract void destroy();

  /**
   * Free all resources from managed caches. Same as calling all {@link org.cache2k.Cache#close()} methods.
   * A closed manager cannot be use the create new caches. A new instance of the same name may be requested
   * via {@link #getInstance(String)}.
   *
   * <p>Multiple calls to close have no effect.
   */
  public abstract void close();

  /**
   *
   * @deprecated use {@link #isClosed()}
   */
  public abstract boolean isDestroyed();

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
