package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.spi.CacheManagerLifeCycleListener;
import org.cache2k.core.util.Cache2kVersion;
import org.cache2k.core.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class CacheManagerImpl extends CacheManager {

  private static final Iterable<CacheLifeCycleListener> cacheLifeCycleListeners =
    constructAllServiceImplementations(CacheLifeCycleListener.class);
  private static final Iterable<CacheManagerLifeCycleListener> cacheManagerLifeCycleListeners =
    constructAllServiceImplementations(CacheManagerLifeCycleListener.class);

  /**
   * The service loader works lazy, however, we want to have all implementations constructed.
   * Retrieve all implementations from the service loader and return an read-only iterable
   * backed by an array.
   */
  private static <S> Iterable<S> constructAllServiceImplementations(Class<S> _service) {
    ClassLoader cl = CacheManagerImpl.class.getClassLoader();
    ArrayList<S> li = new ArrayList<S>();
    for (S l : ServiceLoader.load(_service, cl)) {
      li.add(l);
    }
    final S[] a = (S[]) Array.newInstance(_service, li.size());
    li.toArray(a);
    return new Iterable<S>() {
      public Iterator<S> iterator() {
       return new Iterator<S>() {
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
    };
  }

  private Map<String, StackTrace> name2CreationStackTrace = null;
  private final Object lock = new Object();
  private Log log;
  private String name;
  private Map<String, InternalCache> cacheNames = new HashMap<String, InternalCache>();
  private Set<Cache> caches = new HashSet<Cache>();
  private int disambiguationCounter = 1;
  private final Properties properties = new Properties();
  private ClassLoader classLoader;
  private String version;
  private String buildNumber;
  private boolean defaultManager;
  private Cache2kCoreProviderImpl provider;

  public CacheManagerImpl(Cache2kCoreProviderImpl _provider, ClassLoader cl, String _name, boolean _default) {
    provider = _provider;
    defaultManager = _default;
    classLoader = cl;
    name = _name;
    log = Log.getLog(CacheManager.class.getName() + ':' + name);
    buildNumber = Cache2kVersion.getBuildNumber();
    version = Cache2kVersion.getVersion();
    StringBuilder sb = new StringBuilder();
    sb.append("org.cache2k manager starting. ");
    sb.append("name="); sb.append(name);
    sb.append(", version="); sb.append(version);
    sb.append(", build="); sb.append(buildNumber);
    boolean _traceCacheCreation = log.isDebugEnabled();
    sb.append(", defaultImplementation=");
    sb.append(HeapCache.TUNABLE.defaultImplementation.getSimpleName());
    log.info(sb.toString());
    for (CacheManagerLifeCycleListener lc : cacheManagerLifeCycleListeners) {
      lc.managerCreated(this);
    }
    if (_traceCacheCreation) {
      name2CreationStackTrace = new HashMap<String, StackTrace>();
    }
  }

  public static Iterable<CacheLifeCycleListener> getCacheLifeCycleListeners() {
    return cacheLifeCycleListeners;
  }

  public void sendCreatedEvent(Cache c) {
    for (CacheLifeCycleListener e : cacheLifeCycleListeners) {
      e.cacheCreated(c);
    }
  }

  private void sendDestroyedEvent(Cache c) {
    for (CacheLifeCycleListener e : cacheLifeCycleListeners) {
      e.cacheDestroyed(c);
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
          c == ')'
        ) {
        continue;
      }
      if (!Character.isJavaIdentifierPart(c)) {
        throw new IllegalArgumentException(
          "Cache name contains illegal character: '" + c + "', name=\"" + s + "\"");
      }
    }
  }

  static class StackTrace extends Exception { }

  /* called by builder */
  public String newCache(InternalCache c, String _requestedName) {
    checkClosed();
    synchronized (lock) {
      String _name = _requestedName;
      while (cacheNames.containsKey(_name)) {
        _name = _requestedName + "~" + Integer.toString(disambiguationCounter++, 36);
      }
      if (!_requestedName.equals(_name)) {
        log.warn("duplicate name, disambiguating: " + _requestedName + " -> " + _name, new StackTrace());
        if (name2CreationStackTrace != null) {
          log.warn("initial creation of " + _requestedName, name2CreationStackTrace.get(_requestedName));
        }
      }
      checkName(_name);

      if (name2CreationStackTrace != null) {
        name2CreationStackTrace.put(_name, new StackTrace());
      }
      caches.add(c);
      cacheNames.put(_name, c);
      return _name;
    }
  }

  /* called by cache or CM */
  public void cacheDestroyed(Cache c) {
    synchronized (lock) {
      cacheNames.remove(c.getName());
      if (name2CreationStackTrace != null) {
        name2CreationStackTrace.remove(c.getName());
      }
      caches.remove(c);
      sendDestroyedEvent(c);
    }
  }

  @Override
  public boolean isDefaultManager() {
    return defaultManager;
  }

  @Override
  public Cache2kConfiguration getDefaultConfiguration() {
    if (Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER != null) {
      return Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.getDefaultConfiguration(this);
    }
    return new Cache2kConfiguration();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Iterable<Cache> getActiveCaches() {
    return cachesCopy();
  }

  @Override
  public Iterator<Cache> iterator() {
    return cachesCopy().iterator();
  }

  private Iterable<Cache> cachesCopy() {
    Set<Cache> _caches = new HashSet<Cache>();
    synchronized (lock) {
      if (!isClosed()) {
        for (Cache c : caches) {
          if (!c.isClosed()) {
            _caches.add(c);
          }
        }
      }
    }
    return _caches;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K,V> Cache<K,V> getCache(String name) {
    synchronized (lock) {
      Cache c = cacheNames.get(name);
      return c != null && c.isClosed() ? null : c;
    }
  }

  @Override
  public <K, V> Cache<K, V> createCache(final Cache2kConfiguration<K, V> cfg) {
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

  @Override
  public void destroy() {
    close();
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
    synchronized (lock) {
      if (caches == null) {
        return;
      }
      List<Throwable> _suppressedExceptions = new ArrayList<Throwable>();
      for (Cache c : caches) {
        ((InternalCache) c).cancelTimerJobs();
      }
      Set<Cache> _caches = new HashSet<Cache>();
      _caches.addAll(caches);
      for (Cache c : _caches) {
        try {
          c.close();
        } catch (Throwable t) {
          _suppressedExceptions.add(t);
        }
      }
      try {
        for (CacheManagerLifeCycleListener lc : cacheManagerLifeCycleListeners) {
          lc.managerDestroyed(this);
        }
        caches = null;
      } catch (Throwable t) {
        _suppressedExceptions.add(t);
      }
      ((Cache2kCoreProviderImpl) PROVIDER).removeManager(this);
      eventuallyThrowException(_suppressedExceptions);
    }
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
  static void eventuallyThrowException(List<Throwable> _suppressedExceptions) {
    if (_suppressedExceptions.isEmpty()) {
      return;
    }
    Throwable _error = null;
    for (Throwable t : _suppressedExceptions) {
      if (t instanceof Error) { _error = t; break; }
      if (t instanceof ExecutionException &&
        t.getCause() instanceof Error) {
        _error = t.getCause();
        break;
      }
    }
    String _text = "Exception(s) during shutdown";
    if (_suppressedExceptions.size() > 1) {
      _text = " (" + (_suppressedExceptions.size() - 1)+ " more suppressed exceptions)";
    }
    if (_error != null) {
      throw new CacheInternalError(_text, _error);
    }
    throw new CacheException(_text, _suppressedExceptions.get(0));
  }

  @Override
  @Deprecated
  public boolean isDestroyed() {
    return isClosed();
  }

  @Override
  public boolean isClosed() {
    return caches == null;
  }

  /**
   * Used for JSR107 cache manager implementation
   */
  public Object getLockObject() {
    return lock;
  }

  private void checkClosed() {
    if (caches == null) {
      throw new IllegalStateException("CacheManager already closed");
    }
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
    return version;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  public Cache2kCoreProviderImpl getProvider() {
    return provider;
  }

}
