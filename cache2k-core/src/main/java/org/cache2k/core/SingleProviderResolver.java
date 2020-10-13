package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Loads singletons of service provider implementations.
 *
 * @author Jens Wilke
 */
public class SingleProviderResolver {

  private static final Map<Class, Object> PROVIDERS = new HashMap<Class, Object>();

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
   * @return instance of the provider or {@code null} if not found
   * @throws java.lang.LinkageError if there is a problem instantiating the provider
   */
  public static <T> T resolve(Class<T> c) {
    return resolve(c, null);
  }

  /**
   * Return a provider for this interface.
   *
   * @param c the provider interface that is implemented
   * @param defaultImpl if no provider is found, instantiate the default implementation
   * @param <T> type of provider interface
   *
   * @return instance of the provider or {@code null} if not found
   * @throws java.lang.LinkageError if there is a problem instantiating the provider
   */
  @SuppressWarnings("unchecked")
  public static synchronized <T> T resolve(Class<T> c, Class<? extends T> defaultImpl) {
    if (PROVIDERS.containsKey(c)) {
      return (T) PROVIDERS.get(c);
    }
    T impl = null;
    Iterator<T> it = ServiceLoader.load(c).iterator();
    if (it.hasNext()) {
      impl = it.next();
    }
    if (impl == null && defaultImpl != null) {
      try {
        impl = defaultImpl.getConstructor().newInstance();
      } catch (Exception ex) {
        Error err = new LinkageError("Error instantiating " + c.getName(), ex);
        err.printStackTrace();
        throw err;
      }
    }
    PROVIDERS.put(c, impl);
    return impl;
  }

}
