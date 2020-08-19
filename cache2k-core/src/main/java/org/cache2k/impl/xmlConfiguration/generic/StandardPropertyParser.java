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

import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.CacheTypeCapture;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class StandardPropertyParser implements PropertyParser {

  private Map<Class<?>, ValueConverter> type2parser = new HashMap<Class<?>, ValueConverter>();

  private static final Map<String, Long> UNIT2LONG = new HashMap<String, Long>() {{
    put("KiB", 1024L);
    put("MiB", 1024L * 1024);
    put("GiB", 1024L * 1024 * 1024);
    put("TiB", 1024L * 1024 * 1024 * 1024);
    put("k", 1000L);
    put("M", 1000L * 1000);
    put("G", 1000L * 1000 * 1000);
    put("T", 1000L * 1000 * 1000 * 1000);
    put("s", 1000L);
    put("m", 1000L * 60);
    put("h", 1000L * 60 * 60);
    put("d", 1000L * 60 * 60 * 24);
  }};

  public Object parse(Class<?> targetType, String value) throws Exception {
    ValueConverter p = type2parser.get(targetType);
    if (p == null) {
      throw new IllegalArgumentException("Unknown target type: " + targetType);
    }
    return p.parse(value);
  }

  private void addParser(Class<?> type, ValueConverter<?> p) {
    type2parser.put(type, p);
  }

  private void addParser(Class<?> primitiveType, Class<?> type, ValueConverter<?> p) {
    type2parser.put(primitiveType, p);
    type2parser.put(type, p);
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
        if ("true".equals(v)) {
          return true;
        }
        if ("false".equals(v)) {
          return false;
        }
        throw new IllegalArgumentException("no boolean, true/false expected");
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
    addParser(CacheType.class, new ValueConverter<CacheType>() {
      @Override
      public CacheType parse(String v) throws Exception {
        if (!v.contains(".")) {
          v = "java.lang." + v;
        }
        return CacheTypeCapture.of(Class.forName(v));
      }
    });
  }

  public static long parseLongWithUnitSuffix(String v) {
    v = v.replace("_", "");
    long multiplier = 1;
    int pos = v.length();
    while (--pos >= 0 && !Character.isDigit(v.charAt(pos)));
    if (pos < v.length() - 1) {
      String unitSuffix = v.substring(pos + 1);
      Long newMultiplier = UNIT2LONG.get(unitSuffix);
      if (newMultiplier == null) {
        throw new NumberFormatException("Unknown unit suffix in: \"" + v + "\"");
      }
      v = v.substring(0, pos + 1);
      multiplier = newMultiplier;
    }
    return Long.valueOf(v) * multiplier;
  }

}
