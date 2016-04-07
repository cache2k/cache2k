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
import java.util.Arrays;

/**
 * @author Jens Wilke
 */
public class ParserTest {

  public void test() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/config.xml");
    ConfigPullParser pp = new XppConfigParser(is);
    for (;;) {
      int r = pp.next();
      switch (r) {
        case ConfigPullParser.END: return;
        case ConfigPullParser.PROPERTY: System.out.println(pp.getPropertyName() + " = " + Arrays.toString(pp.getPropertyValue().toCharArray())); break;
        case ConfigPullParser.NEST: System.out.println("nest: " + pp.getSectionName()); break;
        case ConfigPullParser.UNNEST: System.out.println("unnest"); break;
      }
    }
  }

}
