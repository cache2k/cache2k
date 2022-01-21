package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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
import org.cache2k.CacheClosedException;
import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.spi.CacheManagerLifeCycleListener;
import org.cache2k.core.log.Log;
import org.cache2k.spi.Cache2kCoreProvider;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class CacheManagerImpl extends CacheManager {

  public static final Cache2kCoreProvider PROVIDER = CacheManager.PROVIDER;

  private static final Iterable<CacheLifeCycleListener> CACHE_LIFE_CYCLE_LISTENERS =
    constructAllServiceImplementations(CacheLifeCycleListener.class);
  private static final Iterable<CacheManagerLifeCycleListener> CACHE_MANAGER_LIFE_CYCLE_LISTENERS =
    constructAllServiceImplementations(CacheManagerLifeCycleListener.class);

  /**
   * The service loader works lazy, however, we want to have all implementations constructed.
   * Retrieve all implementations from the service loader and return an read-only iterable
   * backed by an array.
   */
  @SuppressWarnings("unchecked")
  private static <S> Iterable<S> constructAllServiceImplementations(Class<S> service) {
    ClassLoader cl = CacheManagerImpl.class.getClassLoader();
    ArrayList<S> li = new ArrayList<>();
    Iterator<S> it = ServiceLoader.load(service, cl).iterator();
    while (it.hasNext()) {
      try {
        li.add(it.next());
      } catch (ServiceConfigurationError ex) {
        Log.getLog(CacheManager.class.getName())
          .warn("Error loading service '" + service + "'", ex);
      }
    }
    S[] a = (S[]) Array.newInstance(service, li.size());
    li.toArray(a);
    return () -> new Iterator<S>() {
      private int pos = 0;

      public boolean hasNext() {
        return pos < a.length;
      }

      public S next() {
        return a[pos++];
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private final Object lock = new Object();
  private final Log log;
  private final String name;
  private Map<String, Cache<?, ?>> cacheNames = new HashMap<>();
  private final Properties properties = new Properties();
  private final ClassLoader classLoader;
  private final boolean defaultManager;
  private final Cache2kCoreProviderImpl provider;
  private boolean closing;

  public CacheManagerImpl(Cache2kCoreProviderImpl provider, ClassLoader cl, String name,
                          boolean defaultManager) {
    this.provider = provider;
    this.defaultManager = defaultManager;
    classLoader = cl;
    this.name = name;
    log = Log.getLog(CacheManager.class.getName() + '.' + this.name);
    for (CacheManagerLifeCycleListener lc : CACHE_MANAGER_LIFE_CYCLE_LISTENERS) {
      lc.managerCreated(this);
    }
    logPhase("open");
  }

  public <K, V> void sendCreatedEvent(Cache c, InternalCacheBuildContext<K, V> ctx) {
    for (CacheLifeCycleListener e : CACHE_LIFE_CYCLE_LISTENERS) {
      e.cacheCreated(c, ctx);
    }
  }

  public <K, V> void sendClosedEvent(Cache<K, V> c, InternalCacheCloseContext ctx) {
    for (CacheLifeCycleListener e : CACHE_LIFE_CYCLE_LISTENERS) {
      e.cacheClosed(c, ctx);
    }
  }

  /**
   * Don't accept a cache or manager names with too weird characters.
   *
   * @see org.cache2k.Cache2kBuilder#name(String)
   */
  public static void checkName(String s) {
    for (char c : s.toCharArray()) {
      if (c == '.' ||
          c == '-' ||
          c == '~' ||
          c == ',' ||
          c == '@' ||
          c == ' ' ||
          c == '(' ||
          c == ')' ||
          c == '+' ||
          c == '!' ||
          c == '\'' ||
          c == '%' ||
          c == '#') {
        continue;
      }
      if (c < 32 || c >= 127 || !Character.isJavaIdentifierPart(c)) {
        throw new IllegalArgumentException(
          "Cache name contains illegal character: '" + c + "', name=\"" + s + "\"");
      }
    }
  }

  /**
   *
   * @throws IllegalStateException if cache manager was closed or is closing
   * @throws IllegalStateException if cache already created
   */
  public String newCache(Cache<?, ?> c, String requestedName) {
    synchronized (lock) {
      checkClosed();
      String name = requestedName;
      if (cacheNames.containsKey(name)) {
        throw new IllegalStateException("Cache already created: '" + requestedName + "'");
      }
      checkName(name);
      cacheNames.put(name, c);
      return name;
    }
  }

  /** Called from the cache during close() */
  public void cacheClosed(Cache<?, ?> c) {
    synchronized (lock) {
      cacheNames.remove(c.getName());
    }
  }

  @Override
  public boolean isDefaultManager() {
    return defaultManager;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Iterable<Cache<?, ?>> getActiveCaches() {
    return cachesCopy();
  }

  private Iterable<Cache<?, ?>> cachesCopy() {
    Set<Cache<?, ?>> caches = new HashSet<>();
    synchronized (lock) {
      if (!isClosed()) {
        for (Cache c : cacheNames.values()) {
          if (!c.isClosed()) {
            caches.add(c);
          }
        }
      }
    }
    return caches;
  }

  private Collection<String> getActiveCacheNames() {
    Set<String> caches = new HashSet<>();
    synchronized (lock) {
      if (!isClosed()) {
        for (Cache c : cacheNames.values()) {
          if (!c.isClosed()) {
            caches.add(c.getName());
          }
        }
      }
    }
    return caches;
  }

  @Override
  public Iterable<String> getConfiguredCacheNames() {
    return Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.getConfiguredCacheNames(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> Cache<K, V> getCache(String name) {
    synchronized (lock) {
      Cache c = cacheNames.get(name);
      return c != null && c.isClosed() ? null : c;
    }
  }

  @Override
  public <K, V> Cache<K, V> createCache(Cache2kConfig<K, V> cfg) {
    return Cache2kBuilder.of(cfg).manager(this).build();
  }

  @Override
  public void clear() {
    for (Cache c : cachesCopy()) {
      try {
        c.clear();
      } catch (CacheClosedException ignore) { }
    }
  }

  /**
   * The shutdown takes place in two phases. First all caches are notified to
   * cancel their scheduled timer jobs, after that the shutdown is done. Cancelling
   * the timer jobs first is needed, because there may be cache stacking and
   * a timer job of one cache may call an already closed cache.
   *
   * <p>Rationale exception handling: Exceptions on shutdown just could silently
   * ignored, because a shutdown is done any way. Exceptions could be happened
   * long before in a parallel task, shutdown is the last point where this could
   * be propagated to the application. Silently ignoring is bad anyway, because
   * this will cause that serious problems keep undetected. The exception and
   * error handling within the cache tries everything that exceptions will be
   * routed through as early and directly as possible.
   */
  @Override
  public void close() {
    if (isDefaultManager() && getClass().getClassLoader() == classLoader) {
      log.info("Closing default CacheManager");
    }
    Iterable<Cache<?, ?>> caches;
    synchronized (lock) {
      if (closing) {
        return;
      }
      caches = cachesCopy();
      closing = true;
    }
    logPhase("close");
    List<Throwable> suppressedExceptions = new ArrayList<>();
    for (Cache c : caches) {
      ((InternalCache) c).cancelTimerJobs();
    }
    for (Cache c : caches) {
      try {
        c.close();
      } catch (Throwable t) {
        suppressedExceptions.add(t);
      }
    }
    try {
      for (CacheManagerLifeCycleListener lc : CACHE_MANAGER_LIFE_CYCLE_LISTENERS) {
        lc.managerDestroyed(this);
      }
    } catch (Throwable t) {
      suppressedExceptions.add(t);
    }
    ((Cache2kCoreProviderImpl) PROVIDER).removeManager(this);
    synchronized (lock) {
      for (Cache c : cacheNames.values()) {
        log.warn("unable to close cache: " + c.getName());
      }
    }
    eventuallyThrowException(suppressedExceptions);
    cacheNames = null;
  }

  /**
   * During the shutdown of the cache manager multiple exceptions can happen from
   * various caches. The list of exceptions gets examined to throw one exception.
   *
   * <p>If an error is present, the first found gets thrown as CacheInternalError.
   * If no error is present the first normal exception gets thrown as CacheException.
   *
   * <p>The suppressed exceptions will be added to the single exception that gets
   * thrown.
   *
   * @throws org.cache2k.core.CacheInternalError if list contains an error
   * @throws org.cache2k.CacheException if list does not contain an error
   */
  static void eventuallyThrowException(List<Throwable> suppressedExceptions) {
    if (suppressedExceptions.isEmpty()) {
      return;
    }
    Throwable error = null;
    for (Throwable t : suppressedExceptions) {
      if (t instanceof Error) { error = t; break; }
      if (t instanceof ExecutionException &&
        t.getCause() instanceof Error) {
        error = t.getCause();
        break;
      }
    }
    String text = "Exception(s) during shutdown";
    if (suppressedExceptions.size() > 1) {
      text = " (" + (suppressedExceptions.size() - 1) + " more suppressed exceptions)";
    }
    if (error != null) {
      throw new CacheInternalError(text, error);
    }
    throw new CacheException(text, suppressedExceptions.get(0));
  }

  @Override
  public Properties getProperties() {
    return properties;
  }

  @Override
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public String getVersion() {
    return provider.getVersion();
  }

  public Cache2kCoreProviderImpl getProvider() {
    return provider;
  }

  @Override
  public boolean isClosed() {
    return closing;
  }

  /**
   * Used for JSR107 cache manager implementation
   */
  public Object getLockObject() {
    return lock;
  }

  private void checkClosed() {
    if (closing) {
      throw new IllegalStateException("CacheManager already closed");
    }
  }

  private void logPhase(String phase) {
    if (log.isDebugEnabled()) {
      log.debug(phase + ": " + getManagerId());
    }
  }

  /**
   * Relevant information to id a manager. Since there could be multiple cache managers for
   * each class loader, better
   */
  private String getManagerId() {
    return "name='" + name +
      "', objectId=" + Integer.toString(System.identityHashCode(this), 36) +
      ", classloaderId=" + Integer.toString(System.identityHashCode(classLoader), 36) +
      ", default=" + defaultManager;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CacheManager(");
    sb.append(getManagerId());
    if (isClosed()) {
      sb.append(", closed=true");
    } else {
      sb.append(", activeCaches=");
      sb.append(getActiveCacheNames());
    }
    return sb.append(')').toString();
  }

}
