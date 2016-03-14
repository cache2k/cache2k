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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * @author Jens Wilke
 */
public class XppConfigParser implements ConfigPullParser {

  LinkedList<String> hierarchy = new LinkedList<String>();
  XmlPullParser input;
  String startName;
  String value;
  String propertyName;
  int eventType;
  boolean startFlag;

  public XppConfigParser(final InputStream is) throws Exception {
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    XmlPullParser xpp = input = factory.newPullParser();
    xpp.setInput(is, null);
    startFlag = true;
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
    while (nextEvent() != XmlPullParser.END_DOCUMENT) {
      switch (eventType) {
        case XmlPullParser.START_TAG :
          if (startName != null) {
            hierarchy.push(startName);
            startName = input.getName();
            return NEST;
          }
          startName = input.getName(); break;
        case XmlPullParser.TEXT :
          value = input.getText(); break;
        case XmlPullParser.END_TAG :
          String _name = input.getName();
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

  private int nextEvent() throws XmlPullParserException, IOException {
    if (startFlag) {
      startFlag = false;
      return eventType = input.getEventType();
    }
    return eventType = input.next();
  }

}
