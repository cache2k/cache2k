package org.cache2k.impl.xmlConfiguration;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

  private String source;
  private int lineNumber;
  private String name;
  private String type;
  private String container;
  private String propertyContext;
  private Map<String, ConfigurationTokenizer.Property> properties = new HashMap<String, ConfigurationTokenizer.Property>();
  private List<ParsedConfiguration> sections = new ArrayList<ParsedConfiguration>();

  public ParsedConfiguration(final String _source, final int _lineNumber) {
    lineNumber = _lineNumber;
    source = _source;
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

  public void setContainer(final String _container) {
    container = _container;
  }

  public String getName() {
    return name;
  }

  public void setName(final String _name) {
    name = _name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String _type) {
    type = _type;
  }

  public String getPropertyContext() {
    return propertyContext;
  }

  public void setPropertyContext(final String _propertyContext) {
    propertyContext = _propertyContext;
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

  public ParsedConfiguration getSection(String _name) {
    for (ParsedConfiguration c : sections) {
      if (_name.equals(c.getName())) {
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

  public ConfigurationTokenizer.Property getPropertyByPath(final String s) {
    int idx = 0;
    String[] _components = s.split("\\.");
    ParsedConfiguration cfg = this;
    while (idx < _components.length - 1) {
      cfg = cfg.getSection(_components[idx++]);
      if (cfg == null) {
        return null;
      }
    }
    ConfigurationTokenizer.Property p = cfg.getPropertyMap().get(_components[idx]);
    if (p == null) {
      return null;
    }
    return p;
  }

  public String toString() {
    return "ParsedSection{" +
      "container=" + container + ", " +
      "name=" + name + ", " +
      "type=" + type;
  }

}
