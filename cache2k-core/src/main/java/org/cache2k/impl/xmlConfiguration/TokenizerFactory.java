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

import java.io.InputStream;

/**
 * Construct a tokenizer to use.
 *
 * @author Jens Wilke
 * @see ConfigurationTokenizer
 */
public interface TokenizerFactory {

  /**
   * @param _source Name of the source, this is used only for exceptions
   * @param in Input stream to read from
   * @param _encoding character encoding to use
   * @return the created tokenizer
   */
  ConfigurationTokenizer createTokenizer(final String _source, InputStream in, String _encoding) throws Exception;

}
