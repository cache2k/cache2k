package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Singleton which provides a top level MarshallerFactory which
 * in turn delegates to marshaller factories that are registered
 * via the service loader.
 *
 * @author Jens Wilke; created: 2014-04-19
 */
public class Marshallers implements MarshallerFactory {

  private static ServiceLoader<? extends MarshallerFactory> loader =
    ServiceLoader.load(MarshallerFactory.class);

  private static MarshallerFactory instance;

  public static MarshallerFactory getInstance() {
    if (instance == null) {
      instance = new Marshallers();
    }
    return instance;
  }

  private Set<MarshallerFactory> factorySet = new HashSet<>();
  private Map<Class<?>, MarshallerFactory> type2factory = new HashMap<>();
  private Map<Class<?>, MarshallerFactory> factoryType2factory = new HashMap<>();

  /**
   * Directly register a marshallerfactory. This is an alternative to the automatic
   * discovery via the service loader. Useful to provide a marshaller for a
   * custom bean and register it within the class load (static constructor section).
   */
  public synchronized void registerMarshallerFactory(MarshallerFactory f) {
    factorySet.add(f);
    type2factory.clear();
    factoryType2factory.clear();
  }

  @Override
  public Class<?> getType() {
    return Object.class;
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public synchronized Marshaller createMarshaller(Class<?> _type) {
    return resolveFactory(_type).createMarshaller(_type);
  }

  @Override
  public synchronized Marshaller createMarshaller(Parameters c) {
    if (c.getMarshallerFactory() != null) {
      MarshallerFactory f = factoryType2factory.get(c.getMarshallerFactory());
      if (f == null) {
        try {
          f = (MarshallerFactory) c.getMarshallerFactory().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
          throw new IllegalArgumentException(e);
        }
        factoryType2factory.put(c.getMarshallerFactory(), f);
      }
      return f.createMarshaller(c);
    }
    try {
      Marshaller m = (Marshaller) c.getMarshallerType().newInstance();
      return m;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
  }

  MarshallerFactory resolveFactory(Class<?> _type) {
    MarshallerFactory f = type2factory.get(_type);
    if (f != null) {
      return f;
    }
    for (MarshallerFactory i : loader) {
      factorySet.add(i);
    }
    for (MarshallerFactory i : factorySet) {
      if (i.getType().isAssignableFrom(_type)) {
        if (f == null || f.getPriority() < i.getPriority()) {
          f = i;
        }
      }
    }
    type2factory.put(_type, f);
    return f;
  }

}
