package org.cache2k.xmlConfiguration;

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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class StandardPropertyParser implements PropertyParser {

  private Map<Class<?>, ValueConverter> type2parser = new HashMap<Class<?>, ValueConverter>();

  private static final Map<String, Long> UNIT2LONG = new HashMap<String, Long>() {{
    put("KiB", 1024L);
    put("MiB", 1024 * 1024L);
    put("GiB", 1024 * 1024 * 1024L);
    put("TiB", 1024 * 1024 * 1024 * 1024L);
    put("kb", 1000L);
    put("kB", 1000L);
    put("KB", 1000L);
    put("MB", 1000 * 1000L);
    put("GB", 1000 * 1000 * 1000L);
    put("TB", 1000 * 1000 * 1000 * 1000L);
    put("s", 1000L);
    put("m", 1000L * 60);
    put("h", 1000L * 60 * 60);
    put("d", 1000L * 60 * 60 * 24);
  }};

  public Object parse(Class<?> _targetType, String _value) throws Exception {
    ValueConverter p = type2parser.get(_targetType);
    if (p == null) {
      throw new IllegalArgumentException("Unknown target type: " + _targetType);
    }
    return p.parse(_value);
  }

  private void addParser(Class<?> _type, ValueConverter<?> p) {
    type2parser.put(_type, p);
  }

  private void addParser(Class<?> _primitiveType, Class<?> _type, ValueConverter<?> p) {
    type2parser.put(_primitiveType, p);
    type2parser.put(_type, p);
  }

  {
    addParser(Integer.TYPE, Integer.class, new ValueConverter<Integer>() {
      @Override
      public Integer parse(final String v) {
        return Integer.valueOf(v);
      }
    });
    addParser(Boolean.TYPE, Boolean.class, new ValueConverter<Boolean>() {
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
    addParser(Long.TYPE, Long.class, new ValueConverter<Long>() {
      @Override
      public Long parse(String v) {
        return parseLongWithUnitSuffix(v);
      }
    });
    addParser(String.class, new ValueConverter<String>() {
      @Override
      public String parse(final String v) {
        return v;
      }
    });
    addParser(Class.class, new ValueConverter<Class>() {
      @Override
      public Class<?> parse(final String v) throws Exception {
        return Class.forName(v);
      }
    });
  }

  static long parseLongWithUnitSuffix(String v) {
    long _multiplier = 1;
    int pos = v.length();
    while(--pos >= 0 && !Character.isDigit(v.charAt(pos)));
    if (pos < v.length() - 1) {
      String _unitSuffix = v.substring(pos + 1);
      Long _newMultiplier = UNIT2LONG.get(_unitSuffix);
      if (_newMultiplier == null) {
        throw new NumberFormatException("Unknown unit suffix in: \"" + v + "\"");
      }
      v = v.substring(0, pos + 1);
      _multiplier = _newMultiplier;
    }
    return Long.valueOf(v) * _multiplier;
  }

}
