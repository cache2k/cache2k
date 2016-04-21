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

import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.Cache2kExtensionProvider;
import org.cache2k.spi.Cache2kManagerProvider;
import org.cache2k.spi.SingleProviderResolver;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke; created: 2013-06-27
 */
public abstract class CacheManager implements Iterable<Cache>, Closeable {

  protected final static Cache2kManagerProvider provider;

  static {
    provider = SingleProviderResolver.getInstance().resolve(Cache2kCoreProvider.class).getManagerProvider();
    ServiceLoader<Cache2kExtensionProvider> _loader =
        ServiceLoader.load(Cache2kExtensionProvider.class, CacheManager.class.getClassLoader());
    for (Cache2kExtensionProvider p : _loader) {
      p.register();
    }
  }

  /**
   * Name of the default cache manager, which is "default" by default. It is also possible
   * to set the default manager name via JNDI context "java:comp/env" and name
   * "org.cache2k.CacheManager.defaultName".
   */
  public static String getDefaultName() {
    return provider.getDefaultName();
  }

  /**
   * Reset the manager name once on application startup.
   */
  public static void setDefaultName(String s) {
    provider.setDefaultName(s);
  }

  /**
   * Get the default cache manager for the current class loader
   */
  public static CacheManager getInstance() {
    return provider.getDefaultManager(null);
  }

  public static CacheManager getInstance(String _name) {
    return provider.getManager(null, _name, null);
  }

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
   * Properties for the cache manager, never null.
   */
  public abstract Properties getProperties();

  public abstract ClassLoader getClassLoader();

}
