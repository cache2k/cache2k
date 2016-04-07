package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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

  private Set<MarshallerFactory> factorySet = new HashSet<MarshallerFactory>();
  private Map<Class<?>, MarshallerFactory> type2factory = new HashMap<Class<?>, MarshallerFactory>();
  private Map<Class<?>, MarshallerFactory> factoryType2factory = new HashMap<Class<?>, MarshallerFactory>();

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
        } catch (Exception e) {
          throw new IllegalArgumentException(e);
        }
        factoryType2factory.put(c.getMarshallerFactory(), f);
      }
      return f.createMarshaller(c);
    }
    try {
      Marshaller m = (Marshaller) c.getMarshallerType().newInstance();
      return m;
    } catch (Exception e) {
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
