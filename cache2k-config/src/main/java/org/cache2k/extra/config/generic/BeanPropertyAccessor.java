package org.cache2k.extra.config.generic;

/*-
 * #%L
 * cache2k config file support
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class BeanPropertyAccessor implements TargetPropertyAccessor {

  private static final String[] PREFIX = new String[]{"get", "is"};

  private final Map<String, Method> gettersLookupMap;

  public BeanPropertyAccessor(Class<?> clazz) {
    gettersLookupMap = generateGetterLookupMap(clazz);
  }

  @Override
  public Collection<String> getNames() {
    return gettersLookupMap.keySet();
  }

  @Override
  public Class<?> getType(String propertyName) {
    return gettersLookupMap.get(propertyName).getReturnType();
  }

  @Override
  public Object access(Object target, String propertyName)
    throws InvocationTargetException, IllegalAccessException {
    return gettersLookupMap.get(propertyName).invoke(target);
  }

  static Map<String, Method> generateGetterLookupMap(Class<?> c) {
    Map<String, Method> map = new HashMap<>();
    for (Method m : c.getMethods()) {
      if (m.getReturnType() == Void.TYPE ||
          m.getParameterCount() != 0) {
        continue;
      }
      if (m.getName().equals("getClass")) { continue; }
      for (String prefix : PREFIX) {
        if (m.getName().startsWith(prefix)) {
          String propertyName = generatePropertyName(prefix, m.getName());
          Method m0 = map.put(propertyName, m);
          if (m0 != null) {
            throw new IllegalArgumentException(
              "Ambiguous getter for property '" + propertyName +
                "' in class '" + c.getSimpleName() + "'");
          }
          break;
        }
      }
    }
    return map;
  }

  static String generatePropertyName(String prefix, String s) {
    return changeFirstCharToLowerCase(s.substring(prefix.length()));
  }

  static String changeFirstCharToLowerCase(String v) {
    return Character.toLowerCase(v.charAt(0)) + v.substring(1);
  }

}
