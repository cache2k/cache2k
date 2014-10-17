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

import java.util.HashMap;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Resolves a service provider by its interface. This is used for the cache2k
 * internal providers. It is assumed that there is only a single provider
 * present for each interface.
 *
 * @author Jens Wilke; created: 2014-06-19
 */
public class SingleProviderResolver {

  static SingleProviderResolver instance;

  public static SingleProviderResolver getInstance() {
    if (instance == null) {
      instance = new SingleProviderResolver();
    }
    return instance;
  }

  private SingleProviderResolver() { }

  private HashMap<Class<?>, Object> type2instance = new HashMap<Class<?>, Object>();

  /**
   * Return a provider for this interface. If a provider previously was
   * resolved, the instance was cached and is returned. No provider
   * or more then one provider is considered a severe error.
   *
   * @param c the provider interface that it is implemented
   * @param <T> type of provider interface
   *
   * @return cached instance of the provider, never null
   * @throws java.lang.LinkageError if none ore more then one providers are present.
   */
  public <T> T resolve(Class<T> c) {
    if (c == null) {
      throw new LinkageError("requested provider interface is null");
    }
    @SuppressWarnings("unchecked")
    T obj = (T) type2instance.get(c);
    if (obj == null) {
      ServiceLoader<T> l = ServiceLoader.load(c);
      Iterator<T> it = l.iterator();
      if (!it.hasNext()) {
         throw new LinkageError("Provider missing for: " + c.getName());
      }
      obj = it.next();
      if (it.hasNext()) {
         throw new LinkageError("Multiple providers for: " + c.getName() + ". " +
           "Got:  " + obj.getClass().getName() + ", " + it.next().getClass().getName());
      }
      type2instance.put(c, obj);
    }
    return obj;
  }

}
