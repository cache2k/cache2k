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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ApplyToBeanTest {

  @Test
  public void readAndApply() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigurationTokenizer pp = new XppConfigTokenizer("/config.xml", is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(pp);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    ApplyConfiguration _apply = new ApplyConfiguration();
    Cache2kConfiguration _bean = new Cache2kConfiguration();
    assertNotNull(cfg.getSection("templates").getSection("expires"));
    _apply.apply(cfg.getSection("caches").getSection("flights"), cfg.getSection("templates"), _bean);
    assertEquals(123, _bean.getEntryCapacity());
    assertEquals(600000, _bean.getResilienceDuration());
    assertEquals(360000, _bean.getExpireAfterWrite());
  }

}
