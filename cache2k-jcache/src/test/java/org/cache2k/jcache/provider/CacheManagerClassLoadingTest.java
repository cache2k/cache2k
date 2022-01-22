package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
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

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import static javax.cache.Cache.Entry;
import static javax.cache.Caching.getCachingProvider;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cache manager with different class loader.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class CacheManagerClassLoadingTest {

  static final String CACHE_NAME = CacheManagerClassLoadingTest.class.getSimpleName();

  /**
   * Request cache manager with different class loader and put a key in the cache that
   * was loaded by that class loader. equals() needs to work and class loaders needs to be
   * identical
   */
  @Test
  public void testCorrectClassLoaderForKey() throws Exception {
    SpecialClassLoader loader = new SpecialClassLoader();
    CachingProvider provider = getCachingProvider();
    CacheManager mgr =
      getCachingProvider().getCacheManager(provider.getDefaultURI(), loader);
    Cache<Object, Object> cache = mgr.createCache(CACHE_NAME, new MutableConfiguration());
    Class keyClass = loader.loadSpecial(DomainKey.class);
    assertThat(loader).isEqualTo(keyClass.getClassLoader());
    Object key = keyClass.newInstance();
    setValue(key, "someKey");
    String someValue = "Value";
    cache.put(key, someValue);
    Entry e = cache.iterator().next();
    Assertions.assertSame(key.getClass().getClassLoader(), e.getKey().getClass().getClassLoader(), "class loaders identical");
    assertThat(e.getKey()).isEqualTo(key);
    mgr.close();
  }

  /**
   * Request cache manager with different class loader and put a value in the cache that
   * was loaded by that class loader. equals() needs to work and class loaders needs to be
   * identical
   */
  @Test
  public void testCorrectClassLoaderForValue() throws Exception {
    SpecialClassLoader loader = new SpecialClassLoader();
    CachingProvider provider = getCachingProvider();
    CacheManager mgr =
      getCachingProvider().getCacheManager(provider.getDefaultURI(), loader);
    Cache<Object, Object> cache = mgr.createCache(CACHE_NAME, new MutableConfiguration());
    Class valueClass = loader.loadSpecial(DomainValue.class);
    assertThat(loader).isEqualTo(valueClass.getClassLoader());
    Object value = valueClass.newInstance();
    setValue(value, "someValue");
    String someKey = "Key";
    cache.put(someKey, value);
    Entry e = cache.iterator().next();
    Assertions.assertSame(value.getClass().getClassLoader(), e.getValue().getClass().getClassLoader(), "class loaders identical");
    assertThat(e.getValue()).isEqualTo(value);
    mgr.close();
  }

  private void setValue(Object o, String value) throws Exception {
    Method m = o.getClass().getMethod("setValue", String.class);
    m.invoke(o, value);
  }

  public static class DomainKey implements Serializable {

    private String value;

    public String getValue() {
      return value;
    }

    public void setValue(final String v) {
      value = v;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DomainKey that = (DomainKey) o;
      return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public static class DomainValue extends DomainKey {

  }

  static class SpecialClassLoader extends ClassLoader {

    Class<?> loadSpecial(Class<?> clazz) {
      String fileName = clazz.getName().replace('.','/') + ".class";
      try {
        InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int count;
        byte[] data = new byte[4096];
        while ((count = in.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, count);
        }
        buffer.flush();
        byte[] bytes = buffer.toByteArray();
        return defineClass(clazz.getName(), bytes, 0, bytes.length);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

  }

}
