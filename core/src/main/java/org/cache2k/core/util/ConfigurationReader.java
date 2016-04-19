package org.cache2k.core.util;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Sets parameter values on an object
 *
 * @author Jens Wilke; created: 2015-03-30
 */
public class ConfigurationReader {

  Object config;

  public ConfigurationReader(Object config) {
    this.config = config;
  }

  /**
   * Search setter method and set the parameter to the given value.
   *
   * @param _parameterName name of the parameter to set
   * @param _content
   */
  public void apply(String _parameterName, String _content)
      throws ClassNotFoundException, InvocationTargetException, IllegalAccessException {
    Method m = findMethod(_parameterName);
    if (m.getParameterTypes().length == 1) {
      Object o = convertSingle(m.getParameterTypes()[0], _content);
      m.invoke(config, o);
    } else if (m.getParameterTypes().length == 2 &&  TimeUnit.class.equals(m.getParameterTypes()[1])) {
      m.invoke(config, convertTime(_content), TimeUnit.MILLISECONDS);
    } else {
      throw new IllegalArgumentException("setter not supported by XML configuration");
    }
  }

  static Object convertSingle(Class<?> _class, String _content) throws ClassNotFoundException {
    if (String.class.equals(_class)) {
      return _content;
    } else if (Integer.class.equals(_class) || Integer.TYPE.equals(_class)) {
      return Integer.parseInt(_content);
    } else if (Long.class.equals(_class) || Long.TYPE.equals(_class)) {
      return Long.parseLong(_content);
    } else if (Boolean.class.equals(_class) || Boolean.TYPE.equals(_class)) {
      if ("true".equals(_content)) {
        return true;
      }
      if ("yes".equals(_content)) {
        return true;
      }
      return false;
    } else if (Class.class.equals(_class)) {
      return Class.forName(_content);
    }
    throw new IllegalArgumentException("Cannot apply parameter type: " + _class);
  }

  Method findMethod(String _parameterName) {
    String _setterName = "set" + Character.toUpperCase(_parameterName.charAt(0)) + _parameterName.substring(1);
    _setterName = _setterName.toLowerCase();
    for (Method m : config.getClass().getMethods()) {
      if (_setterName.equals(m.getName().toLowerCase())) {
        return m;
      }
    }
    throw new IllegalArgumentException("Unknown parameter");
  }

  static long convertTime(String _content) {
    final String[] _UNIT_STRINGS = {"d", "h", "m", "s", "ms"};
    final TimeUnit[] _UNIT_INSTANCES = {
        TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS, TimeUnit.MILLISECONDS};
    for (int i = 0; i < _UNIT_STRINGS.length; i++) {
      String _suffix = _UNIT_STRINGS[i];
      if (_content.endsWith(_suffix)) {
        TimeUnit tu = _UNIT_INSTANCES[i];
        String s = _content.substring(0, _content.length() - _suffix.length());
        return _UNIT_INSTANCES[i].toMillis(Long.parseLong(s));
      }
    }
    throw new IllegalArgumentException("unknown time suffix (must either of: d, h, m, s, ms)");
  }

}
