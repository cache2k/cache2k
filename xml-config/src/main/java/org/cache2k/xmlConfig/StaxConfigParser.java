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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * @author Jens Wilke
 */
public class StaxConfigParser implements ConfigPullParser {

  LinkedList<String> hierarchy = new LinkedList<String>();
  XMLStreamReader input;
  String startName;
  String value;
  String propertyName;

  public StaxConfigParser(final InputStream is) throws Exception {
    XMLInputFactory f = XMLInputFactory.newInstance();
    input = f.createXMLStreamReader(is);
  }

  @Override
  public String getPropertyName() {
    return propertyName;
  }

  @Override
  public String getPropertyValue() {
    return value;
  }

  @Override
  public String getSectionName() {
    return hierarchy.element();
  }

  @Override
  public int next() throws Exception {
    while (input.hasNext()) {
      int _type = input.next();
      switch (_type) {
        case XMLStreamReader.START_ELEMENT :
          if (startName != null) {
            hierarchy.push(startName);
            startName = input.getLocalName();
            return NEST;
          }
          startName = input.getLocalName(); break;
        case XMLStreamReader.CHARACTERS :
          value = input.getText(); break;
        case XMLStreamReader.END_ELEMENT :
          String _name = input.getLocalName();
          if (startName != null && startName.equals(_name)) {
            propertyName = _name;
            startName = null;
            return PROPERTY;
          }
          if (_name.equals(hierarchy.element())) {
            hierarchy.pop();
            return UNNEST;
          }
          break;
      }
    }
    return END;
  }

}
