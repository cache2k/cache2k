package org.cache2k.extra.config.test;

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

import org.cache2k.extra.config.generic.ConfigurationException;
import org.cache2k.extra.config.generic.ConfigurationParser;
import org.cache2k.extra.config.generic.ConfigurationTokenizer;
import org.cache2k.extra.config.generic.ParsedConfiguration;
import org.cache2k.extra.config.generic.StandardVariableExpander;
import org.cache2k.extra.config.generic.StaxConfigTokenizer;
import org.cache2k.extra.config.generic.VariableExpander;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.lang.System.getenv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.extra.config.generic.ConfigurationTokenizer.Property;

/**
 * @author Jens Wilke
 */
public class ParseCompleteTest {

  ParsedConfiguration parse() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new StaxConfigTokenizer("/config.xml", is, null);
    return ConfigurationParser.parse(pp);
  }

  @Test
  public void parseIt() throws Exception {
    ParsedConfiguration topLevel = parse();
    assertThat(topLevel.getPropertyMap().get("version").getValue()).isEqualTo("1.0");
    ParsedConfiguration defaults = topLevel.getSection("defaults");
    assertThat(defaults).isNotNull();
    assertThat(defaults.getSection("cache").getPropertyMap().get("suppressExceptions").getValue()).isEqualTo("true");
    ParsedConfiguration caches = topLevel.getSection("caches");
    assertThat(caches).isNotNull();
    assertThat(caches.getSection("products").getPropertyMap().get("entryCapacity").getValue()).isEqualTo("5");
    assertThat(caches.getSection("products").getSection("eviction"))
      .as("cache has eviction section")
      .isNotNull();
    assertThat(caches.getSection("products").getSection("eviction")
      .getPropertyMap().get("aValue").getValue()).isEqualTo("123");
    assertThat(topLevel.getStringPropertyByPath("caches.products.eviction.aValue")).isEqualTo("123");
    assertThat(topLevel.getStringPropertyByPath("NOEXISTENT.PROPERTY")).isNull();
  }

  @Test
  public void parseAndExpand() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new StaxConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    VariableExpander expander = new StandardVariableExpander();
    expander.expand(cfg);
    String homeDirectory = getenv("HOME");
    assertThat(cfg.getStringPropertyByPath("properties.homeDirectory")).isEqualTo(homeDirectory);
    assertThat(cfg.getStringPropertyByPath("properties.forward")).isEqualTo("123");
    assertThat(cfg.getStringPropertyByPath("caches.hallo.entryCapacity")).isEqualTo("5");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.duplicateName")).isEqualTo("products");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.bValue")).isEqualTo("123");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.cValue")).isEqualTo("123");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.dValue")).isEqualTo("[123]");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.eValue")).isEqualTo("123-products");
    assertThat(cfg.getStringPropertyByPath("caches.products.eviction.directory")).isEqualTo(homeDirectory);
    assertThat(cfg.getStringPropertyByPath("properties.illegalScope")).isEqualTo("${CHACKA.farusimatasa}");
    assertThat(cfg.getStringPropertyByPath("properties.noClose")).isEqualTo("${ENV.HOME");
  }

  @Test
  public void cyclicReferenceProtection() throws Exception {
    assertThatCode(() -> {
      String fileName = "/cyclic-variable.xml";
      InputStream is = this.getClass().getResourceAsStream(fileName);
      ConfigurationTokenizer pp = new StaxConfigTokenizer(fileName, is, null);
      ParsedConfiguration cfg = ConfigurationParser.parse(pp);
      VariableExpander expander = new StandardVariableExpander();
      expander.expand(cfg);
    }).isInstanceOf(ConfigurationException.class);
  }

  @Test
  public void parseViaStax() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new StaxConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    is = this.getClass().getResourceAsStream("/config.xml");
    pp = new StaxConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg2 = ConfigurationParser.parse(pp);
    compare(cfg, cfg2);
  }

  /** Recursively compare the parsed configuration objects */
  void compare(ParsedConfiguration c1, ParsedConfiguration c2) {
    assertThat(c2.getName())
      .as("name")
      .isEqualTo(c1.getName());
    assertThat(c2.getPropertyContext())
      .as("context")
      .isEqualTo(c1.getPropertyContext());
    assertThat(extractSortedKeys(c2.getPropertyMap()))
      .as("property keys")
      .isEqualTo(extractSortedKeys(c1.getPropertyMap()));
    for (Property p : c1.getPropertyMap().values()) {
      Property p2 = c2.getPropertyMap().get(p.getName());
      assertThat(p2.getSource()).isEqualTo(p.getSource());
      assertThat(p2.getLineNumber()).isEqualTo(p.getLineNumber());
      assertThat(p2.getName()).isEqualTo(p.getName());
      assertThat(p2.getValue()).isEqualTo(p.getValue());
    }
    assertThat(extractNames(c2.getSections()))
      .as("section names")
      .isEqualTo(extractNames(c1.getSections()));
    assertThat(c2.getSections().size())
      .as("section count")
      .isEqualTo(c1.getSections().size());
    for (int i = 0; i < c1.getSections().size(); i++) {
      compare(c1.getSections().get(i), c2.getSections().get(i));
    }
  }

  List<String> extractSortedKeys(Map<String, ?> m) {
    List<String> l = new ArrayList<String>();
    l.addAll(m.keySet());
    Collections.sort(l);
    return l;
  }

  List<String> extractNames(Collection<ParsedConfiguration> lc) {
    List<String> l = new ArrayList<String>();
    for (ParsedConfiguration c : lc)
    if (c.getName() != null) {
      l.add(c.getName());
    }
    return l;
  }

}
