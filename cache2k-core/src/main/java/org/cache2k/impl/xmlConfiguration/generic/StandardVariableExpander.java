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

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight and straight forward variable expansion.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class StandardVariableExpander implements VariableExpander {

  /**
   * Initial contents of the scopes. This map gets copied for each
   * expansion process, so the scope gets shadowed by the XML elements.
   */
  private final Map<String, ValueAccessor> scope2resolver = new HashMap<String, ValueAccessor>();

  private static final String PROPERTIES = "PROPERTIES";

  {
    scope2resolver.put("env", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, String variable) {
        return System.getenv(variable);
      }
    });
    scope2resolver.put("sys", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, String variable) {
        return System.getProperty(variable);
      }
    });
    scope2resolver.put("top", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, String variable) {
        ConfigurationTokenizer.Property p =
          ctx.getTopLevelConfiguration().getPropertyByPath(variable);
        return checkAndReturnValue(p);
      }
    });
    scope2resolver.put("", new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, String variable) {
        ConfigurationTokenizer.Property p =
          ctx.getCurrentConfiguration().getPropertyByPath(variable);
        return checkAndReturnValue(p);
      }
    });
    scope2resolver.put(PROPERTIES, new ValueAccessor() {
      @Override
      public String get(ExpanderContext ctx, String variable) {
        ParsedConfiguration pc = ctx.getTopLevelConfiguration().getSection("properties");
        if (pc == null) {
          return null;
        }
        ConfigurationTokenizer.Property p = pc.getPropertyMap().get(variable);
        return checkAndReturnValue(p);
      }
    });
  }

  @Override
  public void expand(ParsedConfiguration cfg) {
    new Process(cfg, new HashMap<String, ValueAccessor>(scope2resolver)).expand();
  }

  private static class Process implements ExpanderContext {

    private final Map<String, ValueAccessor> scope2resolver;
    private final ParsedConfiguration top;
    private ParsedConfiguration current;
    private int forwardReference = 0;
    private ConfigurationTokenizer.Property lastTroublemaker;

    Process(ParsedConfiguration top, Map<String, ValueAccessor> scope2resolver) {
      this.top = top;
      this.scope2resolver = scope2resolver;
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
          throw new ConfigurationException("Cyclic reference", lastTroublemaker);
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
          lastTroublemaker = p;
        }
      }
      for (ParsedConfiguration c2 : cfg.getSections()) {
        String context = c2.getPropertyContext();
        ValueAccessor savedAccessor = null;
        if (context != null) {
          savedAccessor = scope2resolver.get(context);
          final ParsedConfiguration localScope = c2;
          scope2resolver.put(context, new ValueAccessor() {
            @Override
            public String get(ExpanderContext ctx, String variable) {
              return localScope.getStringPropertyByPath(variable);
            }
          });
        }
        recurse(c2);
        if (savedAccessor != null) {
          scope2resolver.put(context, savedAccessor);
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
     * Scan for sequences that look like a variable reference in the form of
     * {@code ${scope.variablename}} lookup and replace the variable with the expanded value.
     *
     * @return the identical string if nothing was expanded, a replacement string if
     * something was expanded
     */
    private String expand(String s) {
      int idx = 0;
      int endIdx;
      for (; ; idx = endIdx) {
        idx = s.indexOf("${", idx);
        if (idx < 0) {
          return s;
        }
        endIdx = s.indexOf('}', idx);
        if (endIdx < 0) {
          return s;
        }
        int scopeIdx = s.indexOf('.', idx);
        String scope;
        if (scopeIdx >= 0) {
          scope = s.substring(idx + 2, scopeIdx);
        } else {
          scope = PROPERTIES;
          scopeIdx = idx + 1;
        }
        int defaultIdx = s.indexOf(":-", idx);
        int varEndIdx = endIdx;
        String defaultValue = null;
        if (defaultIdx >= 0 && defaultIdx < endIdx) {
          defaultValue = s.substring(defaultIdx + 2, endIdx);
          varEndIdx = defaultIdx;
        }
        String varName = s.substring(scopeIdx + 1, varEndIdx);
        ValueAccessor r = scope2resolver.get(scope);
        if (r == null) {
          continue;
        }
        String substitutionString = r.get(this, varName);
        if (substitutionString == null) {
          if (defaultValue == null) {
            continue;
          }
          substitutionString = defaultValue;
        }
        s = s.substring(0, idx) + substitutionString + s.substring(endIdx + 1);
        endIdx = idx + substitutionString.length();
      }
    }

  }

  private String checkAndReturnValue(ConfigurationTokenizer.Property p) {
    if (p == null) { return null; }
    if (!p.isExpanded()) {
      throw new NeedsExpansion();
    }
    return p.getValue();
  }

}
