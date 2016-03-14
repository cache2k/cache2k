package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
