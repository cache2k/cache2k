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

/**
 * @author Jens Wilke
 */
public class ConfigurationFileParser {

  public static ParsedItemsContainer parse(ConfigurationParser _parser) throws Exception {
    ParsedItemsContainer c = new ParsedItemsContainer();
    parseTopLevelSections(_parser, c);
    return c;
  }

  private static void parseSection(ConfigurationParser _parser, ParsedItemsContainer _container) throws Exception {
    for (;;) {
      ConfigurationParser.Item _item = _parser.next();
      if (_item == null) {
        throw new ConfigurationException("null item", _parser.getSource(), _parser.getLineNumber());
      }
      if (_item instanceof ConfigurationParser.Unnest) {
        return;
      }
      if (_item instanceof ConfigurationParser.Property) {
        ConfigurationParser.Property p = (ConfigurationParser.Property) _item;
        _container.addProperty((ConfigurationParser.Property) _item);
      }
      if (_item instanceof ConfigurationParser.Nest) {
        parseSections(_parser, _container);
      }
    }
  }

  private static void parseSections(final ConfigurationParser _parser, final ParsedItemsContainer _container) throws Exception {
    for (;;) {
      ConfigurationParser.Item _item = _parser.next();
      if (_item == null) {
        return;
      }
      if (_item instanceof ConfigurationParser.Unnest) {
        return;
      }
      if (!(_item instanceof ConfigurationParser.Nest)) {
        throw new ConfigurationException("section start expected", _item);
      }
      ConfigurationParser.Nest _sectionStart = (ConfigurationParser.Nest) _item;
      ParsedItemsContainer _nestedContainer = new ParsedItemsContainer();
      _nestedContainer.setName(_sectionStart.getSectionName());
      parseSection(_parser, _nestedContainer);
      _container.addSection(_nestedContainer);
    }
  }

  private static void parseTopLevelSections(final ConfigurationParser _parser, final ParsedItemsContainer _container) throws Exception {
    ConfigurationParser.Item _item = _parser.next();
    for (;;) {
      _item = _parser.next();
      if (_item == null) {
        return;
      }
      if (_item instanceof ConfigurationParser.Unnest) {
        return;
      }
      if (!(_item instanceof ConfigurationParser.Nest)) {
        throw new ConfigurationException("section start expected", _item);
      }
      ConfigurationParser.Nest _sectionStart = (ConfigurationParser.Nest) _item;
      ParsedItemsContainer _nestedContainer = new ParsedItemsContainer();
      _nestedContainer.setName(_sectionStart.getSectionName());
      parseSections(_parser, _nestedContainer);
      _container.addSection(_nestedContainer);
    }
  }

}
