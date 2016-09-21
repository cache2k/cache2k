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

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class ParseCompleteTest {

  @Test
  public void parseIt() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new NewXppConfigParser("/config.xml", is);
    Configuration _topLevel = ConfigurationParser.parse(pp);
    assertEquals("1.0", _topLevel.getPropertyMap().get("version").getValue());
    Configuration _defaults = _topLevel.getSection("defaults");
    assertNotNull(_defaults);
    assertEquals("true", _defaults.getSection("cache").getPropertyMap().get("suppressExceptions").getValue());
    Configuration _caches = _topLevel.getSection("caches");
    assertNotNull(_caches);
    assertEquals("5", _caches.getSection("products").getPropertyMap().get("entryCapacity").getValue());
    assertNotNull("cache has eviction section", _caches.getSection("products").getSection("eviction"));
    assertEquals("123", _caches.getSection("products").getSection("eviction").getPropertyMap().get("aValue").getValue());
    assertEquals("123", _topLevel.getStringPropertyByPath("caches.products.eviction.aValue"));
  }

  @Test
  public void parseAndExpand() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new NewXppConfigParser("/config.xml", is);
    Configuration cfg = ConfigurationParser.parse(pp);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    String _homeDirectory = System.getenv("HOME");
    assertEquals(_homeDirectory, cfg.getStringPropertyByPath("properties.user.homeDirectory"));
    assertEquals("123", cfg.getStringPropertyByPath("properties.user.forward"));
    assertEquals("5", cfg.getStringPropertyByPath("caches.hallo.entryCapacity"));
    assertEquals("products", cfg.getStringPropertyByPath("caches.products.eviction.duplicateName"));
    assertEquals("123", cfg.getStringPropertyByPath("caches.products.eviction.bValue"));
    assertEquals("123", cfg.getStringPropertyByPath("caches.products.eviction.cValue"));
    assertEquals("[123]", cfg.getStringPropertyByPath("caches.products.eviction.dValue"));
    assertEquals("123-products", cfg.getStringPropertyByPath("caches.products.eviction.eValue"));
    assertEquals(_homeDirectory, cfg.getStringPropertyByPath("caches.products.eviction.directory"));
    assertEquals("${CHACKA.farusimatasa}", cfg.getStringPropertyByPath("properties.user.illegalScope"));
    assertEquals("${ENV.HOME", cfg.getStringPropertyByPath("properties.user.noClose"));
  }

  @Test(expected = ConfigurationException.class)
  public void cyclicReferenceProtection() throws Exception {
    String _fileName = "/cyclic-variable.xml";
    InputStream is = this.getClass().getResourceAsStream(_fileName);
    ConfigurationTokenizer pp = new NewXppConfigParser(_fileName, is);
    Configuration cfg = ConfigurationParser.parse(pp);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
  }

}
