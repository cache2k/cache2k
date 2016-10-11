package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Scans all setters on target type and provides mutation method.
 *
 * @author Jens Wilke
 */
public class Mutator {

  private final static String SETTER_PREFIX = "set";

  final Map<String, Method> settersLookupMap;

  public Mutator(Class<?> _class) {
    settersLookupMap = generateSetterLookupMap(_class);
  }

  public Class<?> getType(String _propertyName) {
    Method m = settersLookupMap.get(_propertyName);
    if ( m== null) {
      return null;
    }
    return m.getParameterTypes()[0];
  }

  public void mutate(Object _target, String _propertyName, Object _value)
    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Method m = settersLookupMap.get(_propertyName);
    m.invoke(_target, _value);
  }

  static Map<String, Method> generateSetterLookupMap(Class<?> c) {
    Map<String, Method> map = new HashMap<String, Method>();
    for (Method m : c.getMethods()) {
      if (m.getName().startsWith(SETTER_PREFIX) && m.getReturnType() == Void.TYPE && (m.getParameterTypes().length == 1)) {
        map.put(generatePropertyNameFromSetter(m.getName()), m);
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
