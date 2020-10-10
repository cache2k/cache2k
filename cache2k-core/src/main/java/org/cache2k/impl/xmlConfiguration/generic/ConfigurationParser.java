package org.cache2k.impl.xmlConfiguration.generic;

/*
 * #%L
 * cache2k core implementation
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
 * @author Jens Wilke
 */
public class ConfigurationParser {

  public static ParsedConfiguration parse(ConfigurationTokenizer parser) throws Exception {
    ParsedConfiguration c = new ParsedConfiguration(parser.getSource(), parser.getLineNumber());
    parseTopLevelSections(parser, c);
    ConfigurationTokenizer.Item item = parser.next();
    return c;
  }

  private static void parseSection(ConfigurationTokenizer parser, ParsedConfiguration container)
    throws Exception {
    for (;;) {
      ConfigurationTokenizer.Item item = parser.next();
      if (item == null) {
        throw new ConfigurationException("null item", parser);
      }
      if (item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (item instanceof ConfigurationTokenizer.Property) {
        container.addProperty((ConfigurationTokenizer.Property) item);
      } else if (item instanceof ConfigurationTokenizer.Nest) {
        parseSections(((ConfigurationTokenizer.Nest) item).getSectionName(), parser, container);
      }
    }
  }

  private static void parseSections(String containerName, ConfigurationTokenizer parser,
                                    ParsedConfiguration container) throws Exception {
    boolean maybeSection = true;
    for (;;) {
      ConfigurationTokenizer.Item item = parser.next();
      if (item == null) {
        return;
      }
      if (item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (item instanceof ConfigurationTokenizer.Property && maybeSection) {
        ParsedConfiguration nestedContainer =
          new ParsedConfiguration(parser.getSource(), parser.getLineNumber());
        nestedContainer.setName(containerName);
        nestedContainer.setPropertyContext(containerName);
        nestedContainer.setContainer("#DIRECT");
        nestedContainer.addProperty((ConfigurationTokenizer.Property) item);
        parseSection(parser, nestedContainer);
        container.addSection(nestedContainer);
        return;
      }
      if (!(item instanceof ConfigurationTokenizer.Nest)) {
        throw new ConfigurationException("section start expected", item);
      }
      ConfigurationTokenizer.Nest sectionStart = (ConfigurationTokenizer.Nest) item;
      ParsedConfiguration nestedContainer =
        new ParsedConfiguration(parser.getSource(), parser.getLineNumber());
      nestedContainer.setName(sectionStart.getSectionName());
      nestedContainer.setPropertyContext(sectionStart.getSectionName());
      nestedContainer.setContainer(containerName);
      parseSection(parser, nestedContainer);
      container.addSection(nestedContainer);
      maybeSection = false;
    }
  }

  private static void parseTopLevelSections(ConfigurationTokenizer parser,
                                            ParsedConfiguration container) throws Exception {
    ConfigurationTokenizer.Item item = parser.next();
    if (!(item instanceof ConfigurationTokenizer.Nest)) {
      throw new ConfigurationException("start expected", item);
    }
    for (;;) {
      item = parser.next();
      if (item == null) {
        return;
      }
      if (item instanceof ConfigurationTokenizer.Unnest) {
        return;
      }
      if (item instanceof ConfigurationTokenizer.Property) {
        container.addProperty((ConfigurationTokenizer.Property) item);
      }  else if (item instanceof ConfigurationTokenizer.Nest) {
        ConfigurationTokenizer.Nest sectionStart = (ConfigurationTokenizer.Nest) item;
        ParsedConfiguration nestedContainer =
          new ParsedConfiguration(parser.getSource(), parser.getLineNumber());
        nestedContainer.setName(sectionStart.getSectionName());
        parseSections(
          ((ConfigurationTokenizer.Nest) item).getSectionName(), parser, nestedContainer);
        if (nestedContainer.getPropertyMap().isEmpty() &&
          nestedContainer.getSections().size() == 1 &&
          nestedContainer.getSections().get(0).getContainer().equals("#DIRECT")) {
          nestedContainer = nestedContainer.getSections().get(0);
        }
        container.addSection(nestedContainer);
      }
    }
  }

}
