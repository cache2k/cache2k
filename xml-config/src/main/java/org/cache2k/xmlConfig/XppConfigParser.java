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
