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

import java.io.InputStream;

/**
 * @author Jens Wilke
 */
public class ConfigurationParser {

  private final ConfigurationTokenizerFactory factory;

  public ConfigurationParser(ConfigurationTokenizerFactory f) {
    factory = f;
  }

  public ParsedConfiguration parse(String _source, InputStream in, String _encoding) throws Exception {
    return parse(factory.create(_source, in, _encoding));
  }

  static ParsedConfiguration parse(ConfigurationTokenizer _parser) throws Exception {
    ParsedConfiguration c = new ParsedConfiguration(_parser.getSource(), _parser.getLineNumber());
    parseTopLevelSections(_parser, c);
    ConfigurationTokenizer.Item _item = _parser.next();
    return c;
  }

  private static void parseSection(ConfigurationTokenizer _parser, ParsedConfiguration _container) throws Exception {
    for (;;) {
      ConfigurationTokenizer.Item _item = _parser.next();
      if (_item == null) {
        throw new ConfigurationException("null item", _parser.getSource(), _parser.getLineNumber());
      }
      if (_item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (_item instanceof ConfigurationTokenizer.Property) {
        _container.addProperty((ConfigurationTokenizer.Property) _item);
      } else if (_item instanceof ConfigurationTokenizer.Nest) {
        parseSections(_parser, _container);
      }
    }
  }

  private static void parseSections(final ConfigurationTokenizer _parser, final ParsedConfiguration _container) throws Exception {
    for (;;) {
      ConfigurationTokenizer.Item _item = _parser.next();
      if (_item == null) {
        return;
      }
      if (_item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (!(_item instanceof ConfigurationTokenizer.Nest)) {
        throw new ConfigurationException("section start expected", _item);
      }
      ConfigurationTokenizer.Nest _sectionStart = (ConfigurationTokenizer.Nest) _item;
      ParsedConfiguration _nestedContainer = new ParsedConfiguration(_parser.getSource(), _parser.getLineNumber());
      _nestedContainer.setName(_sectionStart.getSectionName());
      _nestedContainer.setPropertyContext(_sectionStart.getSectionName());
      parseSection(_parser, _nestedContainer);
      _container.addSection(_nestedContainer);
    }
  }

  private static void parseTopLevelSections(final ConfigurationTokenizer _parser, final ParsedConfiguration _container) throws Exception {
    ConfigurationTokenizer.Item _item = _parser.next();
    if (!(_item instanceof ConfigurationTokenizer.Nest)) {
      throw new ConfigurationException("start expected", _item);
    }
    for (;;) {
      _item = _parser.next();
      if (_item == null) {
        return;
      }
      if (_item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (_item instanceof ConfigurationTokenizer.Property) {
        _container.addProperty((ConfigurationTokenizer.Property) _item);
      }  else if (_item instanceof ConfigurationTokenizer.Nest) {
        ConfigurationTokenizer.Nest _sectionStart = (ConfigurationTokenizer.Nest) _item;
        ParsedConfiguration _nestedContainer = new ParsedConfiguration(_parser.getSource(), _parser.getLineNumber());
        _nestedContainer.setName(_sectionStart.getSectionName());
        parseSections(_parser, _nestedContainer);
        _container.addSection(_nestedContainer);
      }
    }
  }

}
