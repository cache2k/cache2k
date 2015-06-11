package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
        ServiceLoader.load(Cache2kExtensionProvider.class);
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
  public synchronized static CacheManager getInstance() {
    return provider.getDefaultManager(null);
  }

  public synchronized static CacheManager getInstance(String _name) {
    return provider.getManager(null, _name, null);
  }

  public abstract String getName();

  public abstract Iterator<Cache> iterator();

  public abstract Cache getCache(String name);

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

  public abstract Properties getProperties();

  public abstract ClassLoader getClassLoader();

}
