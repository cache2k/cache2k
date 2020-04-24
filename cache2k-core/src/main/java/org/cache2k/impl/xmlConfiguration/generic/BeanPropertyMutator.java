package org.cache2k.impl.xmlConfiguration.generic;

/*
 * #%L
 * cache2k implementation
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Scans all setters on target type and provides mutation method.
 *
 * @author Jens Wilke
 */
public class BeanPropertyMutator {

  private final static String SETTER_PREFIX = "set";

  private final Map<String, Method> settersLookupMap;

  public BeanPropertyMutator(Class<?> clazz) {
    settersLookupMap = generateSetterLookupMap(clazz);
  }

  public Class<?> getType(String propertyName) {
    Method m = settersLookupMap.get(propertyName);
    if (m == null) {
      return null;
    }
    return m.getParameterTypes()[0];
  }

  public void mutate(Object target, String propertyName, Object value)
    throws IllegalAccessException, InvocationTargetException {
    Method m = settersLookupMap.get(propertyName);
    m.invoke(target, value);
  }

  /**
   * Don't have a setter for a class, but use the cache type. {@code Cache2kConfiguration.setKeyType()}
   * TODO: generic setter preference?
   */
  private static boolean preferCacheTypeAndNotClass(Class<?> c) {
    return !Class.class.equals(c);
  }

  static Map<String, Method> generateSetterLookupMap(Class<?> c) {
    Map<String, Method> map = new HashMap<String, Method>();
    for (Method m : c.getMethods()) {
      if (m.getName().startsWith(SETTER_PREFIX) &&
        m.getReturnType() == Void.TYPE &&
        (m.getParameterTypes().length == 1) &&
        preferCacheTypeAndNotClass(m.getParameterTypes()[0])) {
        String propertyName = generatePropertyNameFromSetter(m.getName());
        Method m0 = map.put(propertyName, m);
        if (m0 != null) {
          throw new IllegalArgumentException(
            "Ambiguous setter for property '" + propertyName + "' in class '" + c.getSimpleName() + "'");
        }
      }
    }
    return map;
  }

  static String generatePropertyNameFromSetter(String s) {
    return changeFirstCharToLowerCase(s.substring(SETTER_PREFIX.length()));
  }

  static String changeFirstCharToLowerCase(String v) {
    return Character.toLowerCase(v.charAt(0)) + v.substring(1);
  }

}
