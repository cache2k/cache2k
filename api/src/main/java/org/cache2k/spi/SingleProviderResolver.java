package org.cache2k.spi;

/*
 * #%L
 * cache2k API only package
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a service provider by its interface. This is used for the cache2k
 * internal providers.
 *
 * <p/>This class is in principle similar to the {@link java.util.ServiceLoader}.
 * The design is a little bit simpler, because there is always one provider for each
 * interface. We cannot use the original ServiceLoader, since it is not working on
 * android.
 *
 * @author Jens Wilke; created: 2014-06-19
 * @see <a href="https://code.google.com/p/android/issues/detail?id=59658">android service loader issue</a>
 */
public class SingleProviderResolver {

  private final static Map<ClassLoader, SingleProviderResolver> loader2resolver =
      new HashMap<ClassLoader, SingleProviderResolver>();

  /**
   * Get a resolver instance which resolves classes with the same class loader as this class is in.
   */
  public static SingleProviderResolver getInstance() {
    return getInstance(SingleProviderResolver.class.getClassLoader());
  }

  public synchronized static SingleProviderResolver getInstance(ClassLoader cl) {
    SingleProviderResolver r = loader2resolver.get(cl);
    if (r == null) {
      r = new SingleProviderResolver(cl);
      loader2resolver.put(cl, r);
    }
    return r;
  }

  private final ClassLoader classLoader;
  private final Map<Class<?>, Object> type2instance = new HashMap<Class<?>, Object>();

  private SingleProviderResolver(ClassLoader cl) {
    classLoader = cl;
  }

  /**
   * Return a provider for this interface. If a provider previously was
   * resolved, a cached instance is returned.
   *
   * @param c the provider interface that is implemented
   * @param <T> type of provider interface
   *
   * @return cached instance of the provider, never null
   * @throws java.lang.LinkageError if no provider is found.
   */
  @SuppressWarnings("unchecked")
  public <T> T resolve(Class<T> c) {
    if (c == null) {
      throw new NullPointerException("requested provider interface is null");
    }
    synchronized (type2instance) {
      T obj = (T) type2instance.get(c);
      if (obj == null) {
        obj = loadProvider(c);
        type2instance.put(c, obj);
      }
      return obj;
    }
  }

  @SuppressWarnings("unchecked")
  <T> T loadProvider(Class<T> c) {
    try {
      String _className = readFile("org/cache2k/services/" + c.getName());
      if (_className == null) {
        throw new LinkageError("Provider missing for: " + c.getName());
      }
      return (T) classLoader.loadClass(_className).newInstance();
    } catch (Exception e) {
      throw new LinkageError("Error instantiating " + c.getName() + ", got exception: " +e.toString());
    }
  }

  String readFile(String _name) throws IOException {
    InputStream in = classLoader.getResourceAsStream(_name);
    if (in == null) {
      throw new IOException("Class resource file not found: " + _name);
    }
    LineNumberReader r = new LineNumberReader(new InputStreamReader(in));
    String l = r.readLine();
    while (l != null) {
      if (!l.startsWith("#")) {
        return l;
      }
      l = r.readLine();
    }
    return null;
  }

}
