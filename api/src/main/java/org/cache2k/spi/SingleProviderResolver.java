package org.cache2k.spi;

/*
 * #%L
 * cache2k API only package
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
 * <p/>This class is in principle similar to the {@link java.util.ServiceLoader}.
 * The design is a little bit simpler, because there is always one provider for each
 * interface. We cannot use the original ServiceLoader, since it is not working on
 * android.
 *
 * @author Jens Wilke; created: 2014-06-19
 * @see <a href="https://code.google.com/p/android/issues/detail?id=59658">android service loader issue</a>
 */
public class SingleProviderResolver {

  static Map<ClassLoader, SingleProviderResolver> loader2resolver =
      new HashMap<ClassLoader, SingleProviderResolver>();

  public static SingleProviderResolver getInstance() {
    return getInstance(Thread.currentThread().getContextClassLoader());
  }

  public synchronized static SingleProviderResolver getInstance(ClassLoader cl) {
    SingleProviderResolver r = loader2resolver.get(cl);
    if (r == null) {
      r = new SingleProviderResolver(cl);
      loader2resolver.put(cl, r);
    }
    return r;
  }

  private ClassLoader classLoader;
  private HashMap<Class<?>, Object> type2instance = new HashMap<Class<?>, Object>();

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
  public <T> T resolve(Class<T> c) {
    if (c == null) {
      throw new Error("requested provider interface is null");
    }
    @SuppressWarnings("unchecked")
    T obj = (T) type2instance.get(c);
    if (obj == null) {
      obj = loadProvider(c);
      type2instance.put(c, obj);
    }
    return obj;
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
      throw new LinkageError("Error instantiating " + c.getName() + ", got " +e.toString());
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
