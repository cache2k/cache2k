package org.cache2k.extra.config.generic;

/*-
 * #%L
 * cache2k config file support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the complete parsed configuration or parts of it.
 *
 * @author Jens Wilke
 */
public class ParsedConfiguration implements SourceLocation {

  private final String source;
  private final int lineNumber;
  private String name;
  private String type;
  private String container;
  private String propertyContext;
  private final Map<String, ConfigurationTokenizer.Property> properties = new HashMap<>();
  private final List<ParsedConfiguration> sections = new ArrayList<>();

  public ParsedConfiguration(String source, int lineNumber) {
    this.lineNumber = lineNumber;
    this.source = source;
  }

  public String getSource() {
    return source;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Element name containing a section or bean.
   */
  public String getContainer() {
    return container;
  }

  public void setContainer(String v) {
    this.container = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getType() {
    return type;
  }

  public void setType(String v) {
    type = v;
  }

  public String getPropertyContext() {
    return propertyContext;
  }

  public void setPropertyContext(String v) {
    propertyContext = v;
  }

  public Map<String, ConfigurationTokenizer.Property> getPropertyMap() {
    return properties;
  }

  public List<ParsedConfiguration> getSections() {
    return sections;
  }


  public void addProperty(ConfigurationTokenizer.Property p) {
    if ("name".equals(p.getName())) {
      name = p.getValue();
    }
    if ("type".equals(p.getName())) {
      type = p.getValue();
    }
    properties.put(p.getName(), p);
  }

  public void addSection(ParsedConfiguration c) {
    sections.add(c);
  }

  public ParsedConfiguration getSection(String name) {
    for (ParsedConfiguration c : sections) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  public String getStringPropertyByPath(String s) {
    ConfigurationTokenizer.Property p = getPropertyByPath(s);
    if (p == null) { return null; }
    return p.getValue();
  }

  public ConfigurationTokenizer.Property getPropertyByPath(String s) {
    int idx = 0;
    String[] components = s.split("\\.");
    ParsedConfiguration cfg = this;
    while (idx < components.length - 1) {
      cfg = cfg.getSection(components[idx++]);
      if (cfg == null) {
        return null;
      }
    }
    ConfigurationTokenizer.Property p = cfg.getPropertyMap().get(components[idx]);
    return p;
  }

  public String toString() {
    return "ParsedSection{" +
      "container=" + container + ", " +
      "name=" + name + ", " +
      "type=" + type;
  }

}
