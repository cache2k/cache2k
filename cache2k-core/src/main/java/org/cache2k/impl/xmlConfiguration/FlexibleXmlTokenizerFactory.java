package org.cache2k.impl.xmlConfiguration;

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

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;

/**
 * Use XmlPullParser for XML parsing if present (on Android) or fall back to Stax for
 * standard Java environments.
 *
 * @author Jens Wilke
 */
public class FlexibleXmlTokenizerFactory implements TokenizerFactory {

  private final TokenizerFactory realTokenizer = detectUsableTokenizer();

  TokenizerFactory detectUsableTokenizer() {
    try {
      XmlPullParser.class.toString();
      return new XppConfigTokenizer.Factory();
    } catch (LinkageError ex) {
      return new StaxConfigTokenizer.Factory();
    }
  }

  @Override
  public ConfigurationTokenizer createTokenizer(final String _source, final InputStream in, final String _encoding)
    throws Exception {
    return realTokenizer.createTokenizer(_source, in, _encoding);
  }

}
