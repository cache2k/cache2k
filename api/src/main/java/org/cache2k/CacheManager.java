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
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.Cache2kExtensionProvider;
import org.cache2k.spi.Cache2kManagerProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * @author Jens Wilke; created: 2013-06-27
 */
public abstract class CacheManager implements Iterable<Cache>, Closeable {

  protected static Cache2kManagerProvider PROVIDER;

  static {
    PROVIDER = SingleProviderResolver.resolveMandatory(Cache2kCoreProvider.class).getManagerProvider();
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
   * <p>With the server-side addon it is also possible to set the default manager name via JNDI context
   * "java:comp/env" and name "org.cache2k.CacheManager.defaultName".
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
   * True if this is the default manager of the application, returned by {@link #getInstance()}
   */
  public abstract boolean isDefaultManager();

  /**
   * Return the effective default configuration for this manager. A different default
   * configuration may be provided by the configuration system.
   *
   * @return mutable configuration instance containing the effective configuration defaults
   */
  public abstract Cache2kConfiguration getDefaultConfiguration();

  public abstract String getName();

  /**
   * Returns all caches that were not closed before. If the cache manager is closed by itself, always
   * returns nothing.
   */
  public abstract Iterator<Cache> iterator();

  public abstract <K,V> Cache<K,V> getCache(String name);

  /** Clear all caches associated to this cache manager */
  public abstract void clear();

  /**
   * @deprecated Use {@link #close()}
   */
  public abstract void destroy();

  /**
   * Free all resources from managed caches. If there is unwritten data, it is flushed, if needed.
   * Same as calling all {@link org.cache2k.Cache#close()} methods. Calling this method is more effective,
   * since it tries to close all caches concurrently as fast as possible.
   */
  public abstract void close();

  /**
   *
   * @deprecated use {@link #isClosed()}
   */
  public abstract boolean isDestroyed();

  public abstract boolean isClosed();

  /**
   * Properties for the cache manager, never null. By default the properties are empty.
   * Cache clients may store arbitrary information.
   */
  public abstract Properties getProperties();

  public abstract ClassLoader getClassLoader();

}
