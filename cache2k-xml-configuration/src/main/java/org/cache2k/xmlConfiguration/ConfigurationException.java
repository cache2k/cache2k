package org.cache2k.xmlConfiguration;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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

import org.cache2k.CacheException;

/**
 * Exception indicating something is wrong with the external (XML) configuration.
 * We do not use this exception inside the API and core package, instead
 * {@link IllegalArgumentException} is used.
 *
 * @author Jens Wilke
 */
public class ConfigurationException extends CacheException {

  public ConfigurationException(final String message) {
    super(message);
  }

  public ConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public ConfigurationException(final String message, final String _source) {
    super(message + " at " + _source);
  }

  public ConfigurationException(final String _message, final SourceLocation _location) {
    super(_message + " at " + _location.getSource() + ":" + _location.getLineNumber());
  }

}
