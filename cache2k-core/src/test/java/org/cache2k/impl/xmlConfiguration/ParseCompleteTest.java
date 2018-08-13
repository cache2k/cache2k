package org.cache2k.impl.xmlConfiguration;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.impl.xmlConfiguration.ConfigurationException;
import org.cache2k.impl.xmlConfiguration.ConfigurationParser;
import org.cache2k.impl.xmlConfiguration.ConfigurationTokenizer;
import org.cache2k.impl.xmlConfiguration.ParsedConfiguration;
import org.cache2k.impl.xmlConfiguration.StandardVariableExpander;
import org.cache2k.impl.xmlConfiguration.StaxConfigTokenizer;
import org.cache2k.impl.xmlConfiguration.VariableExpander;
import org.cache2k.impl.xmlConfiguration.XppConfigTokenizer;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ParseCompleteTest {

  ParsedConfiguration parse() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new XppConfigTokenizer("/config.xml", is, null);
    return ConfigurationParser.parse(pp);
  }

  @Test
  public void parseIt() throws Exception {
    ParsedConfiguration _topLevel = parse();
    assertEquals("1.0", _topLevel.getPropertyMap().get("version").getValue());
    ParsedConfiguration _defaults = _topLevel.getSection("defaults");
    assertNotNull(_defaults);
    assertEquals("true", _defaults.getSection("cache").getPropertyMap().get("suppressExceptions").getValue());
    ParsedConfiguration _caches = _topLevel.getSection("caches");
    assertNotNull(_caches);
    assertEquals("5", _caches.getSection("products").getPropertyMap().get("entryCapacity").getValue());
    assertNotNull("cache has eviction section", _caches.getSection("products").getSection("eviction"));
    assertEquals("123", _caches.getSection("products").getSection("eviction").getPropertyMap().get("aValue").getValue());
    assertEquals("123", _topLevel.getStringPropertyByPath("caches.products.eviction.aValue"));
    assertNull(_topLevel.getStringPropertyByPath("NOEXISTENT.PROPERTY"));
  }

  @Test
  public void parseAndExpand() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new XppConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    String _homeDirectory = System.getenv("HOME");
    assertEquals(_homeDirectory, cfg.getStringPropertyByPath("properties.homeDirectory"));
    assertEquals("123", cfg.getStringPropertyByPath("properties.forward"));
    assertEquals("5", cfg.getStringPropertyByPath("caches.hallo.entryCapacity"));
    assertEquals("products", cfg.getStringPropertyByPath("caches.products.eviction.duplicateName"));
    assertEquals("123", cfg.getStringPropertyByPath("caches.products.eviction.bValue"));
    assertEquals("123", cfg.getStringPropertyByPath("caches.products.eviction.cValue"));
    assertEquals("[123]", cfg.getStringPropertyByPath("caches.products.eviction.dValue"));
    assertEquals("123-products", cfg.getStringPropertyByPath("caches.products.eviction.eValue"));
    assertEquals(_homeDirectory, cfg.getStringPropertyByPath("caches.products.eviction.directory"));
    assertEquals("${CHACKA.farusimatasa}", cfg.getStringPropertyByPath("properties.illegalScope"));
    assertEquals("${ENV.HOME", cfg.getStringPropertyByPath("properties.noClose"));
  }

  @Test(expected = ConfigurationException.class)
  public void cyclicReferenceProtection() throws Exception {
    String _fileName = "/cyclic-variable.xml";
    InputStream is = this.getClass().getResourceAsStream(_fileName);
    ConfigurationTokenizer pp = new XppConfigTokenizer(_fileName, is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
  }

  @Test
  public void parseViaStax() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new XppConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    is = this.getClass().getResourceAsStream("/config.xml");
    pp = new StaxConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg2 = ConfigurationParser.parse(pp);
    compare(cfg, cfg2);
  }

  /** Recursively compare the parsed configuration objects */
  void compare(ParsedConfiguration c1, ParsedConfiguration c2) {
    assertEquals("name", c1.getName(), c2.getName());
    assertEquals("context", c1.getPropertyContext(), c2.getPropertyContext());
    assertEquals("property keys", extractSortedKeys(c1.getPropertyMap()), extractSortedKeys(c2.getPropertyMap()));
    for (ConfigurationTokenizer.Property p : c1.getPropertyMap().values()) {
      ConfigurationTokenizer.Property p2 = c2.getPropertyMap().get(p.getName());
      assertEquals(p.getSource(), p2.getSource());
      assertEquals(p.getLineNumber(), p2.getLineNumber());
      assertEquals(p.getName(), p2.getName());
      assertEquals(p.getValue(), p2.getValue());
    }
    assertEquals("section names", extractNames(c1.getSections()), extractNames(c2.getSections()));
    assertEquals("section count", c1.getSections().size(), c2.getSections().size());
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
