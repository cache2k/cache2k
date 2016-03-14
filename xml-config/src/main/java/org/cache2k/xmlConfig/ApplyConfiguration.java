package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.impl.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Read configuration from a pull parser and apply it to the object.
 *
 * @author Jens Wilke
 */
public class ApplyConfiguration {

  private final static String _SETTER_PREFIX = "set";

  private static volatile Map<Class<?>, Map<String, Method>> settersLookupMap =
    new HashMap<Class<?>, Map<String, Method>>();
  private static Map<Class<?>, FieldParser> type2parser = new HashMap<Class<?>, FieldParser>();
  private static Log log = Log.getLog(ApplyConfiguration.class);

  private LinkedList<Object> objectStack = new LinkedList<Object>();
  private LinkedList<Map<String, Method>> settersStack = new LinkedList<Map<String, Method>>();
  private Object obj;
  private ConfigPullParser parser;
  private Map<String, Method> setters;

  public ApplyConfiguration(final Object _obj, final ConfigPullParser _parser) {
    obj = _obj;
    parser = _parser;
    setters = getSetterMap(obj.getClass());
  }

  public void parse() throws Exception {
    for (;;) {
      int _next = parser.next();
      switch (_next) {
        case ConfigPullParser.END: return;
        case ConfigPullParser.UNNEST:
          if (objectStack.isEmpty()) {
            return;
          }
          obj = objectStack.pop();
          setters = settersStack.pop();
          break;
        case ConfigPullParser.PROPERTY:
          property(parser.getPropertyName(), parser.getPropertyValue());
          break;
      }
    }
  }

  private void property(String _name, String _value) {
    Method m = setters.get(_name);
    if (m == null) {
      log.warn("Unknown configuration property: " + _name);
      return;
    }
    Class<?> params[] = m.getParameterTypes();
    if (params.length == 1) {
      FieldParser p = type2parser.get(params[0]);
      if (p == null) {
        log.warn("Unknown property type, name=" + _name + ", type=" + params[0].getName());
        return;
      }
      try {
        m.invoke(obj, p.parse(_value));
      } catch (Exception ex) {
        log.warn("Exception setting configuration property " + _name, ex);
      }
    }
  }

  static Map<String, Method> getSetterMap(Class<?> c) {
    Map<String, Method> _property2methodMap = settersLookupMap.get(c);
    if (_property2methodMap == null) {
      Map<Class<?>, Map<String, Method>> _copy = new HashMap<Class<?>, Map<String, Method>>(settersLookupMap);
      _copy.put(c,  _property2methodMap = generateSetterLookupMap(c));
      settersLookupMap = _copy;
    }
    return _property2methodMap;
  }

  static Map<String, Method> generateSetterLookupMap(Class<?> c) {
    Map<String, Method> map = new HashMap<String, Method>();
    for (Method m : c.getMethods()) {
      if (m.getName().startsWith(_SETTER_PREFIX) && m.getReturnType() == Void.TYPE && (
          (m.getParameterTypes().length == 1 && type2parser.containsKey(m.getParameterTypes()[0])) ||
          (m.getParameterTypes().length == 2 && m.getParameterTypes()[1] == TimeUnit.class)
        )) {
        map.put(generatePropertyNameFromSetter(m.getName()), m);
      }
    }
    return map;
  }

  static String generatePropertyNameFromSetter(String s) {
    return changeFirstCharToLowerCase(s.substring(_SETTER_PREFIX.length()));
  }

  static String changeFirstCharToLowerCase(String v) {
    return Character.toLowerCase(v.charAt(0)) + v.substring(1);
  }

  private static void addParser(Class<?> _type, FieldParser<?> p) {
    type2parser.put(_type, p);
  }

  private static void addParser(Class<?> _primitiveType, Class<?> _type, FieldParser<?> p) {
    type2parser.put(_primitiveType, p);
    type2parser.put(_type, p);
  }

  static {
    addParser(Integer.TYPE, Integer.class, new FieldParser<Integer>() {
      @Override
      public Integer parse(final String v) {
        return Integer.valueOf(v);
      }
    });
    addParser(Boolean.TYPE, Boolean.class, new FieldParser<Boolean>() {
      @Override
      public Boolean parse(String v) {
        v = v.toLowerCase();
        return !(
          "off".equals(v) ||
          "false".equals(v) ||
          "disable".equals(v) ||
          "n".equals(v) ||
          "no".equals(v));
      }
    });
    addParser(Long.TYPE, Long.class, new FieldParser<Long>() {
      @Override
      public Long parse(final String v) {
        return Long.valueOf(v);
      }
    });
    addParser(String.class, new FieldParser<String>() {
      @Override
      public String parse(final String v) {
        return v;
      }
    });
    addParser(Class.class, new FieldParser<Class>() {
      @Override
      public Class<?> parse(final String v) throws Exception {
        return Class.forName(v);
      }
    });

  }

}
