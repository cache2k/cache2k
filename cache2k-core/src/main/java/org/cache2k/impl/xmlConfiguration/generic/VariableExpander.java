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

/**
 * Runs through all properties in the configuration, checks for variable references and expand them.
 *
 * @author Jens Wilke
 */
public interface VariableExpander {

  void expand(ParsedConfiguration cfg);

  interface ExpanderContext {
    ParsedConfiguration getTopLevelConfiguration();
    ParsedConfiguration getCurrentConfiguration();
  }

  interface ValueAccessor {
    /**
     *
     * @param ctx current expander context
     * @param variable name of the variable to retrieve
     * @return the value or null, if nothing known
     * @throws NeedsExpansion if a property is referenced that needs expansion
     */
    String get(ExpanderContext ctx, String variable);
  }

  class NeedsExpansion extends RuntimeException {}

}
