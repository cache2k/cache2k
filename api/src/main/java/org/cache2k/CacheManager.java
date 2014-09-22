package org.cache2k;

/*
 * #%L
 * cache2k api only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import java.util.Iterator;

/**
 * @author Jens Wilke; created: 2013-06-27
 */
public abstract class CacheManager implements Iterable<Cache> {

  private static CacheManager defaultManager;
  private static String defaultName = "default";

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
   * Get the default cache manager for the class loader
   */
  public synchronized static CacheManager getInstance() {
    if (defaultManager != null && !defaultManager.isDestroyed()) {
      return defaultManager;
    }
    try {
      defaultManager = (CacheManager)
        Class.forName("org.cache2k.impl.CacheManagerImpl").newInstance();
    } catch (Exception e) {
      throw new Error("cache2k implementation not found, cache2k-core.jar missing?", e);
    }
    return defaultManager;
  }

  public abstract String getName();

  public abstract Iterator<Cache> iterator();

  /** Clear all caches associated to this cache manager */
  public abstract void clear();

  /**
   * Destroy all caches associated to this cache manager.
   */
  public abstract void destroy();

  public abstract boolean isDestroyed();

}
