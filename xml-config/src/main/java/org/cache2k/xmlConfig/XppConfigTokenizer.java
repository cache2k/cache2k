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
public class XppConfigTokenizer extends AbstractConfigurationTokenizer {

  private final XmlPullParser input;
  private LinkedList<String> hierarchy = new LinkedList<String>();
  private String startName;
  private String value;
  private boolean startFlag = true;

  XppConfigTokenizer(final String _source, final InputStream is, final String _encoding)
    throws XmlPullParserException {
    super(_source);
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    input = factory.newPullParser();
    input.setInput(is, _encoding);
  }

  @Override
  public int getLineNumber() {
    return input.getLineNumber();
  }

  @Override
  public Item next() throws Exception {
    int _eventType;
    while ((_eventType = nextEvent()) != XmlPullParser.END_DOCUMENT) {
      switch (_eventType) {
        case XmlPullParser.START_TAG :
          if (startName != null) {
            hierarchy.push(startName);
            startName = input.getName();
            return returnNest(hierarchy.element());
          }
          startName = input.getName(); break;
        case XmlPullParser.TEXT :
          value = input.getText();
          break;
        case XmlPullParser.END_TAG :
          String _name = input.getName();
          if (startName != null && startName.equals(_name)) {
            startName = null;
            return returnProperty(_name, value);
          }
          if (_name.equals(hierarchy.element())) {
            hierarchy.pop();
            return returnUnnest();
          }
          break;
      }
    }
    return null;
  }

  private int nextEvent() throws XmlPullParserException, IOException {
    if (startFlag) {
      startFlag = false;
      return input.getEventType();
    }
    return input.next();
  }

  public static class Factory implements ConfigurationTokenizerFactory {

    @Override
    public ConfigurationTokenizer create(final String _source, final InputStream in, final String _encoding)
      throws XmlPullParserException {
      return new XppConfigTokenizer(_source, in, _encoding);
    }
  }

}
