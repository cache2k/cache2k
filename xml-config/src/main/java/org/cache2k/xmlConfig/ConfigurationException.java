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

/**
 * @author Jens Wilke
 */
public class ConfigurationException extends RuntimeException {

  public ConfigurationException(final String message, final ConfigurationTokenizer.Item _item) {
    super(message + " at " + _item.getSource() + ":" + _item.getLineNumber());
  }

  public ConfigurationException(final String message, final String _source, final int _lineNumber) {
    super(message + " at " + _source + ":" + _lineNumber);
  }

}
