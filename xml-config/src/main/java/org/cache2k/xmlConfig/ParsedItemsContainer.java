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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class ParsedItemsContainer {

  private String name;
  private Map<String, ConfigurationParser.Property> properties = new HashMap<String, ConfigurationParser.Property>();
  private List<ParsedItemsContainer> sections = new ArrayList<ParsedItemsContainer>();

  public String getName() {
    ConfigurationParser.Property p = properties.get("name");
    if (p != null) {
      return p.getValue();
    }
    return name;
  }

  public void setName(final String _name) {
    name = _name;
  }

  public Map<String, ConfigurationParser.Property> getPropertyMap() {
    return properties;
  }

  public Collection<ParsedItemsContainer> getSections() {
    return sections;
  }

  public void addProperty(ConfigurationParser.Property p) { properties.put(p.getName(), p); }

  public void addSection(ParsedItemsContainer c) {
    sections.add(c);
  }

  public ParsedItemsContainer getSection(String _name) {
    for (ParsedItemsContainer c : sections) {
      if (_name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

}
