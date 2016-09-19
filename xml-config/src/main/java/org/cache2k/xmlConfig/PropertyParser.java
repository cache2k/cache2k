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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class PropertyParser {

  private Map<Class<?>, FieldParser> type2parser = new HashMap<Class<?>, FieldParser>();

  public Object parse(Class<?> _targetType, String _value) throws Exception {
    FieldParser p = type2parser.get(_targetType);
    return p.parse(_value);
  }

  private void addParser(Class<?> _type, FieldParser<?> p) {
    type2parser.put(_type, p);
  }

  private void addParser(Class<?> _primitiveType, Class<?> _type, FieldParser<?> p) {
    type2parser.put(_primitiveType, p);
    type2parser.put(_type, p);
  }

  {
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
