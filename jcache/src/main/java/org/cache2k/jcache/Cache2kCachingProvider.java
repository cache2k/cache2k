package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import org.cache2k.impl.Cache2kManagerProviderImpl;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.Cache2kManagerProvider;
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
 * @author Jens Wilke; created: 2014-10-19
 */
public class Cache2kCachingProvider implements CachingProvider {

  Cache2kManagerProvider forwardProvider;
  Map<ClassLoader, Map<URI, Cache2kManagerAdapter>> classLader2uri2cache =
      new WeakHashMap<ClassLoader, Map<URI, Cache2kManagerAdapter>>();

  {
    forwardProvider = SingleProviderResolver.getInstance().resolve(Cache2kCoreProvider.class).getManagerProvider();
  }

  private Object getLockObject() {
    return ((Cache2kManagerProviderImpl) forwardProvider).getLockObject();
  }

  public URI name2Uri(String _name) {
    try {
      return new URI("file", _name, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String uri2Name(URI uri) {
    return uri.getSchemeSpecificPart();
  }

  @Override
  public CacheManager getCacheManager(URI uri, ClassLoader cl, Properties p) {
    synchronized (getLockObject()) {
      Map<URI, Cache2kManagerAdapter> map = classLader2uri2cache.get(cl);
      if (map == null) {
        map = new HashMap<URI, Cache2kManagerAdapter>();
        classLader2uri2cache.put(cl, map);
      }
      Cache2kManagerAdapter cm = map.get(uri);
      if (cm != null && !cm.isClosed()) {
        return cm;
      }
      cm = new Cache2kManagerAdapter(
          this,
          forwardProvider.getManager(cl, uri2Name(uri), p));
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
    return name2Uri(forwardProvider.getDefaultName());
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
