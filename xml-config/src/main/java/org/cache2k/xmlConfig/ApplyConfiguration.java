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

import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationWithSections;
import org.cache2k.core.util.Log;

import java.lang.reflect.InvocationTargetException;

/**
 * Apply a parsed configuration to a configuration bean.
 *
 * @author Jens Wilke
 */
public class ApplyConfiguration {

  private Log log = Log.getLog(ApplyConfiguration.class);
  private PropertyParser propertyParser = new StandardPropertyParser();

  public void apply(ParsedConfiguration cfg, ParsedConfiguration _templates, Object _bean) throws Exception {
    ConfigurationTokenizer.Property _include = cfg.getPropertyMap().get("include");
    if (_include != null) {
      for (String _template : _include.getValue().split(",")) {
        ParsedConfiguration c2 = _templates.getSection(_template);
        if (c2 == null) {
          throw new ConfigurationException("Template not found: \"" + _template + "\"", _include.getSource(), _include.getLineNumber());
        }
        apply(c2, _templates, _bean);
      }
    }
    applyProperyValues(cfg, _bean);
    if (!(_bean instanceof ConfigurationWithSections)) {
      return;
    }
    ConfigurationWithSections _configurationWithSections = (ConfigurationWithSections) _bean;
    for(ParsedConfiguration sc : cfg.getSections()) {
      if (sc.getType() == null) {
        throw new ConfigurationException("section type missing", sc.getSource(), sc.getLineNumber());
      }
      Class<?> _type;
      try {
         _type = Class.forName(sc.getType());
      } catch (ClassNotFoundException ex) {
        throw new ConfigurationException(
          "section configuration class not found '" + sc.getType() + "'", sc.getSource(), sc.getLineNumber());
      }
      ConfigurationSection _sectionBean =
        _configurationWithSections.getSections().getSection((Class<ConfigurationSection>) _type);
      if (_sectionBean == null) {
        _sectionBean = (ConfigurationSection)  _type.newInstance();
        _configurationWithSections.getSections().add(_sectionBean);
      }
      apply(sc, _templates, _sectionBean);
    }
  }

  private void applyProperyValues(final ParsedConfiguration cfg, final Object _bean) throws Exception {
    Mutator m = new Mutator(_bean.getClass());
    for (ConfigurationTokenizer.Property p : cfg.getPropertyMap().values()) {
      Class<?> _propertyType = m.getType(p.getName());
      if (_propertyType == null) {
        log.warn("Unknown property '" + p.getName() + "': " + p.getSource() + ':' + p.getLineNumber());
        continue;
      }
      m.mutate(_bean, p.getName(), propertyParser.parse(_propertyType, p.getValue()));
    }
  }

}
