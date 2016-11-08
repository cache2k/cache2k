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
 * Lightweight and straight forward variable expansion.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
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
        ConfigurationTokenizer.Property p = ctx.getTopLevelConfiguration().getPropertyByPath(_variable);
        return checkAndReturnValue(p);
      }
    });
    scope2resolver.put("", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, final String _variable) {
        ConfigurationTokenizer.Property p = ctx.getCurrentConfiguration().getPropertyByPath(_variable);
        return checkAndReturnValue(p);
      }
    });

  }

  @Override
  public void expand(final ParsedConfiguration cfg) {
    new Process(cfg, new HashMap<String, ValueAccessor>(scope2resolver)).expand();
  }

  private static class Process implements ExpanderContext {

    private final Map<String, ValueAccessor> scope2resolver;
    private final ParsedConfiguration top;
    private ParsedConfiguration current;
    private int forwardReference = 0;
    private ConfigurationTokenizer.Property lastTroubemaker;

    Process(final ParsedConfiguration _top, final Map<String, ValueAccessor> _scope2resolver) {
      top = _top;
      scope2resolver = _scope2resolver;
    }

    /**
     * Recurse into configuration objects and expand variables. It may happen that a property
     * refers to another property which is not yet expanded. In this case we repeat the expansion.
     * If the number of properties that are affected is not lowering per iteration there must
     * be a cyclic reference.
     */
    private void expand() {
      do {
        int lastCounter = forwardReference;
        forwardReference = 0;
        recurse(top);
        if (lastCounter > 0 && lastCounter == forwardReference) {
          throw new ConfigurationException("Cyclic reference", lastTroubemaker.getSource(), lastTroubemaker.getLineNumber());
        }
      } while(forwardReference > 0);
    }

    private void recurse(ParsedConfiguration cfg) {
      current = cfg;
      for (ConfigurationTokenizer.Property p : cfg.getPropertyMap().values()) {
        if (p.isExpanded()) {
          continue;
        }
        String v0 = p.getValue();
        try {
          String v = expand(v0);
          if (v0 != v) {
            p.setValue(v);
          }
          p.setExpanded(true);
        } catch(NeedsExpansion ex) {
          forwardReference++;
          lastTroubemaker = p;
        }
      }
      for (ParsedConfiguration c2 : cfg.getSections()) {
        String _context = c2.getPropertyContext();
        ValueAccessor _savedAccessor = null;
        if (_context != null) {
          _savedAccessor = scope2resolver.get(_context);
          final ParsedConfiguration _localScope = c2;
          scope2resolver.put(_context, new ValueAccessor() {
            @Override
            public String get(final ExpanderContext ctx, final String _variable) {
              return _localScope.getStringPropertyByPath(_variable);
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
    public ParsedConfiguration getCurrentConfiguration() {
      return current;
    }

    @Override
    public ParsedConfiguration getTopLevelConfiguration() {
      return top;
    }

    /**
     * Scan for sequences that look like a variable reference in the form of {@code ${scope.variablename}}
     * lookup and replace the variable with the expanded value.
     *
     * @return the identical string if nothing was expanded, a replacement string if something was expanded
     */
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

  private String checkAndReturnValue(final ConfigurationTokenizer.Property p) {
    if (p == null) { return null; }
    if (!p.isExpanded()) {
      throw new NeedsExpansion();
    }
    return p.getValue();
  }

}
