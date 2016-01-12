package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.spi.Cache2kExtensionProvider;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author Jens Wilke; created: 2013-06-27
 */
public abstract class CacheManager implements Iterable<Cache>, Closeable {

  protected final static String DEFAULT_MANAGER_NAME = "default";

  private static CacheManager defaultManager;
  private static String defaultName = DEFAULT_MANAGER_NAME;
  private static Map<String, CacheManager> name2manager = new HashMap<String, CacheManager>();

  static {
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
    return defaultName;
  }

  /**
   * Reset the manager name once on application startup.
   */
  public static void setDefaultName(String defaultName) {
    if (defaultManager != null) {
      throw new IllegalStateException("default CacheManager already created");
    }
    CacheManager.defaultName = defaultName;
  }

  /**
   * Get the default cache manager for the current class loader
   */
  public synchronized static CacheManager getInstance() {
    if (defaultManager != null && !defaultManager.isDestroyed()) {
      return defaultManager;
    }
    try {
      defaultManager = (CacheManager) getManagerClass().newInstance();
    } catch (Exception e) {
      return implNotFound(e);
    }
    name2manager.put(defaultManager.getName(), defaultManager);
    return defaultManager;
  }

  private static CacheManager implNotFound(Exception e) {
    throw new Error("cache2k implementation not found, cache2k-core.jar missing?", e);
  }

  private static Class<?> getManagerClass() throws ClassNotFoundException {
    return Class.forName("org.cache2k.impl.CacheManagerImpl");
  }

  public synchronized static CacheManager getInstance(String _name) {
    if (defaultName.equals(_name)) {
      return getInstance();
    }
    CacheManager m = name2manager.get(_name);
    if (m != null) { return m; }
    try {
      Class<?> c = getManagerClass();
      Constructor<?> cc = c.getConstructor(String.class);
        m = (CacheManager) cc.newInstance(_name);
    } catch (Exception e) {
      return implNotFound(e);
    }
    name2manager.put(_name, m);
    return m;
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

  public abstract boolean isDestroyed();

}
