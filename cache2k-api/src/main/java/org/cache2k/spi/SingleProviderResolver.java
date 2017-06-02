package org.cache2k.spi;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a service provider by its interface. This is used for the cache2k
 * internal providers.
 *
 * <p>This class is in principle similar to the {@link java.util.ServiceLoader}.
 * The design is a little bit simpler, because there is always one provider for each
 * interface. We cannot use the original ServiceLoader, since it is not working on
 * android.
 *
 * @author Jens Wilke; created: 2014-06-19
 * @see <a href="https://code.google.com/p/android/issues/detail?id=59658">android service loader issue</a>
 */
public class SingleProviderResolver {

  private static Map<Class, Object> providers = new HashMap<Class, Object>();

  /**
   * Return a provider for this interface.
   *
   * @param c the provider interface that is implemented
   * @param <T> type of provider interface
   *
   * @return instance of the provider, never null
   * @throws java.lang.LinkageError if there is a problem instantiating the provider
   *                                or no provider was specified
   */
  public static <T> T resolveMandatory(Class<T> c) {
    T obj = resolve(c);
    if (obj == null) {
      Error err = new LinkageError("No implementation found for: " + c.getName());
      err.printStackTrace();
      throw err;
    }
    return obj;
  }

  /**
   * Return a provider for this interface.
   *
   * @param c the provider interface that is implemented
   * @param <T> type of provider interface
   *
   * @return instance of the provider or null if not found
   * @throws java.lang.LinkageError if there is a problem instantiating the provider
   */
  public synchronized static <T> T resolve(Class<T> c) {
    if (providers.containsKey(c)) {
      return (T) providers.get(c);
    }
    try {
      String _className = readFile("org/cache2k/services/" + c.getName());
      if (_className == null) {
        return null;
      }
      T obj = (T) SingleProviderResolver.class.getClassLoader().loadClass(_className).newInstance();
      providers.put(c, obj);
      return obj;
    } catch (Exception ex) {
      Error err = new LinkageError("Error instantiating " + c.getName(), ex);
      err.printStackTrace();
      throw err;
    }
  }

  private static String readFile(String _name) throws IOException {
    InputStream in = SingleProviderResolver.class.getClassLoader().getResourceAsStream(_name);
    if (in == null) {
      return null;
    }
    LineNumberReader r = new LineNumberReader(new InputStreamReader(in));
    String l = r.readLine();
    while (l != null) {
      if (!l.startsWith("#")) {
        return l;
      }
      l = r.readLine();
    }
    throw new IOException("No class file name in resource: " + _name);
  }

}
