package org.cache2k.impl.xmlConfiguration.generic;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * @author Jens Wilke
 */
public class StaxConfigTokenizer extends AbstractConfigurationTokenizer {

  private XMLStreamReader input;
  private LinkedList<String> hierarchy = new LinkedList<String>();
  private String startName;
  private String value;

  public StaxConfigTokenizer(final String source, final InputStream in, final String encoding)
    throws XMLStreamException {
    super(source);
    XMLInputFactory f = XMLInputFactory.newInstance();
    input = f.createXMLStreamReader(in, encoding);
  }

  @Override
  public int getLineNumber() {
    return input.getLocation().getLineNumber();
  }

  @Override
  public Item next() throws Exception {
    while (input.hasNext()) {
      int type = input.next();
      switch (type) {
        case XMLStreamReader.START_ELEMENT :
          if (startName != null) {
            hierarchy.push(startName);
            startName = input.getLocalName();
            return returnNest(hierarchy.element());
          }
          startName = input.getLocalName(); break;
        case XMLStreamReader.CHARACTERS :
          value = input.getText(); break;
        case XMLStreamReader.END_ELEMENT :
          String name = input.getLocalName();
          if (startName != null && startName.equals(name)) {
            startName = null;
            return returnProperty(name, value);
          }
          if (name.equals(hierarchy.element())) {
            hierarchy.pop();
            return returnUnnest();
          }
          break;
      }
    }
    return null;
  }

  public static class Factory implements TokenizerFactory {

    @Override
    public ConfigurationTokenizer createTokenizer(final String source, final InputStream in, final String encoding)
      throws XMLStreamException {
      return new StaxConfigTokenizer(source, in, encoding);
    }
  }

}
