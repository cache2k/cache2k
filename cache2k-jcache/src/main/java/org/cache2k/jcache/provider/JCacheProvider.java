package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.io.File;
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
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#jcache">
 *   JCache - cache2k User Guide</a>
 */
public class JCacheProvider implements CachingProvider {

  private final Cache2kCoreProvider forwardProvider = org.cache2k.CacheManager.PROVIDER;

  private final Map<ClassLoader, Map<URI, JCacheManagerAdapter>> classLoader2uri2cache =
      new WeakHashMap<ClassLoader, Map<URI, JCacheManagerAdapter>>();

  private Object getLockObject() {
    return ((Cache2kCoreProviderImpl) forwardProvider).getLockObject();
  }

  public URI name2Uri(String name) {
    try {
      return new URI(name);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String uri2Name(URI uri) {
    String s = uri.toString();
    if (uri.getScheme() != null || s.contains(".xml") || s.contains(File.separator)) {
      throw new IllegalArgumentException(
        "Only cache manager name, e.g. new URI(\"name\"), expected in the URI, " +
        "not a file name or path, " +
        "https://cache2k.org/docs/latest/user-guide.html#jcache-uri-exception");
    }
    return s;
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
        Properties managerProperties = cm.getProperties();
        for (Map.Entry e : p.entrySet()) {
          if (!managerProperties.containsKey(e.getKey())) {
            managerProperties.put(e.getKey(), e.getValue());
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
    String defaultName = forwardProvider.getDefaultManagerName(getDefaultClassLoader());
    URI defaultUri = name2Uri(defaultName);
    return defaultUri;
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
    return true;
  }

}
