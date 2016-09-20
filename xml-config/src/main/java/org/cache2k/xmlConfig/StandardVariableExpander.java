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
public class StandardVariableExpander implements VariableExpander {

  private Map<String, ValueAccessor> scope2resolver = new HashMap<String, ValueAccessor>();

  {
    scope2resolver.put("ENV", new ValueAccessor() {
      @Override
      public String get(final ExpanderContext ctx, final String _variable) {
        return System.getenv(_variable);
      }
    });
    scope2resolver.put("PROP", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, final String _variable) {
        return System.getProperty(_variable);
      }
    });
    scope2resolver.put("TOP", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, final String _variable) {
        return ctx.getTopLevelConfiguration().getPathProperty(_variable);
      }
    });
    scope2resolver.put("", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, final String _variable) {
        return ctx.getCurrentConfiguration().getPathProperty(_variable);
      }
    });

  }

  @Override
  public void expand(final Configuration cfg) {
    new Process(cfg, new HashMap<String, ValueAccessor>(scope2resolver)).recurse(cfg);
  }

  private static class Process implements ExpanderContext {

    private final Map<String, ValueAccessor> scope2resolver;
    private final Configuration top;
    private Configuration current;

    public Process(final Configuration _top, final Map<String, ValueAccessor> _scope2resolver) {
      top = _top;
      scope2resolver = _scope2resolver;
    }

    private void recurse(Configuration cfg) {
      for (ConfigurationTokenizer.Property p : cfg.getPropertyMap().values()) {
        String v0 = p.getValue();
        String v = expand(v0);
        if (v0 != v) {
          p.setValue(v);
        }
      }
      for (Configuration c2 : cfg.getSections()) {
        String _context = c2.getPropertyContext();
        ValueAccessor _savedAccessor = null;
        current = c2;
        if (_context != null) {
          _savedAccessor = scope2resolver.get(_context);
          final Configuration _localScope = c2;
          scope2resolver.put(_context, new ValueAccessor() {
            @Override
            public String get(final ExpanderContext ctx, final String _variable) {
              return _localScope.getPathProperty(_variable);
            }
          });
        }
        recurse(c2);
        if (_savedAccessor != null) {
          scope2resolver.put(_context, _savedAccessor);
        }
      }
    }

    @Override
    public Configuration getCurrentConfiguration() {
      return current;
    }

    @Override
    public Configuration getTopLevelConfiguration() {
      return top;
    }

    private String expand(String s) {
      int idx = 0;
      int _endIdx;
      for (; ; idx = _endIdx) {
        idx = s.indexOf("${", idx);
        if (idx < 0) {
          return s;
        }
        _endIdx = s.indexOf('}', idx);
        if (_endIdx < 0) {
          return s;
        }
        int _scopeIdx = s.indexOf('.', idx);
        if (_scopeIdx < 0) {
          continue;
        }
        String _scope = s.substring(idx + 2, _scopeIdx);
        String _varName = s.substring(_scopeIdx + 1, _endIdx);
        ValueAccessor r = scope2resolver.get(_scope);
        if (r == null) {
          continue;
        }
        String _substitutionString = r.get(this, _varName);
        if (_substitutionString == null) {
          continue;
        }
        s = s.substring(0, idx) + _substitutionString + s.substring(_endIdx + 1);
        _endIdx = idx + _substitutionString.length();
      }
    }

  }

}
