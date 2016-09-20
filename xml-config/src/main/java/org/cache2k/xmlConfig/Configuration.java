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
public class Configuration {

  private String name;
  private String propertyContext;
  private Map<String, ConfigurationTokenizer.Property> properties = new HashMap<String, ConfigurationTokenizer.Property>();
  private List<Configuration> sections = new ArrayList<Configuration>();

  public String getName() {
    return name;
  }

  public void setName(final String _name) {
    name = _name;
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

  public Collection<Configuration> getSections() {
    return sections;
  }

  public void addProperty(ConfigurationTokenizer.Property p) {
    if ("name".equals(p.getName())) {
      name = p.getValue();
    }
    properties.put(p.getName(), p);
  }

  public void addSection(Configuration c) {
    sections.add(c);
  }

  public Configuration getSection(String _name) {
    for (Configuration c : sections) {
      if (_name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  public String getPathProperty(String s) {
    int idx = 0;
    String[] _components = s.split("\\.");
    Configuration cfg = this;
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
    return p.getValue();
  }

}
