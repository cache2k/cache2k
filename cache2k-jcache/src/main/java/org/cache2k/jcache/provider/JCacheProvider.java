package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.core.Cache2kCoreProviderImpl;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * JSR107 caching provider on top of cache2k.
 *
 * <p>Attention: Don't move or rename without a transition plan since users might to
 * specify the provider explicitly.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#jcache">JCache - cache2k User Guide</a>
 */
public class JCacheProvider implements CachingProvider {

  private final Cache2kCoreProvider forwardProvider =
    SingleProviderResolver.resolveMandatory(Cache2kCoreProvider.class);

  private final Map<ClassLoader, Map<URI, JCacheManagerAdapter>> classLoader2uri2cache =
      new WeakHashMap<ClassLoader, Map<URI, JCacheManagerAdapter>>();

  private Object getLockObject() {
    return ((Cache2kCoreProviderImpl) forwardProvider).getLockObject();
  }

  public URI name2Uri(String _name) {
    try {
      return new URI(_name);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String uri2Name(URI uri) {
    return uri.toString();
  }

  @Override
  public CacheManager getCacheManager(URI uri, ClassLoader cl, Properties p) {
    if (uri == null) {
      uri = getDefaultURI();
    }
    if (cl == null) {
      cl = getDefaultClassLoader();
    }
    synchronized (getLockObject()) {
      Map<URI, JCacheManagerAdapter> map = classLoader2uri2cache.get(cl);
      if (map == null) {
        map = new HashMap<URI, JCacheManagerAdapter>();
        classLoader2uri2cache.put(cl, map);
      }
      JCacheManagerAdapter cm = map.get(uri);
      if (cm != null && !cm.isClosed()) {
        return cm;
      }
      cm = new JCacheManagerAdapter(
          this,
          forwardProvider.getManager(cl, uri2Name(uri)));
       if (p != null && !p.isEmpty()) {
        Properties _managerProperties = cm.getProperties();
        for (Map.Entry e : p.entrySet()) {
          if (!_managerProperties.containsKey(e.getKey())) {
            _managerProperties.put(e.getKey(), e.getValue());
          }
        }
      }
      map.put(uri, cm);
      return cm;
    }
  }

  @Override
  public ClassLoader getDefaultClassLoader() {
    return getClass().getClassLoader();
  }

  @Override
  public URI getDefaultURI() {
    String _defaultName = forwardProvider.getDefaultManagerName(getDefaultClassLoader());
    URI _defaultUri = name2Uri(_defaultName);
    return _defaultUri;
  }

  @Override
  public Properties getDefaultProperties() {
    return null;
  }

  @Override
  public CacheManager getCacheManager(URI uri, ClassLoader cl) {
    return getCacheManager(uri, cl, getDefaultProperties());
  }

  @Override
  public CacheManager getCacheManager() {
    return getCacheManager(getDefaultURI(), getDefaultClassLoader());
  }

  @Override
  public void close() {
    forwardProvider.close();
  }

  @Override
  public void close(ClassLoader cl) {
    forwardProvider.close(cl);
  }

  @Override
  public void close(URI uri, ClassLoader cl) {
    forwardProvider.close(cl, uri2Name(uri));
  }

  @Override
  public boolean isSupported(OptionalFeature v) {
    if (v == OptionalFeature.STORE_BY_REFERENCE) { return true; }
    return false;
  }

}
