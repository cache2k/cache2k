package org.cache2k.extra.config.generic;

/*
 * #%L
 * cache2k config file support
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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
 * The configuration tokenizer reads in a text representation of a configuration in a tokenized
 * form. This abstracts the actual representation and makes it possible to not support only XML.
 *
 * @author Jens Wilke
 */
public interface ConfigurationTokenizer extends SourceLocation {

  String getSource();

  int getLineNumber();

  /**
   * The next item in the configuration or {@code null} if the end is reached.
   */
  Item next() throws Exception;

  interface Item extends SourceLocation {
    String getSource();
    int getLineNumber();
  }

  interface Property extends Item {
    String getName();
    String getValue();
    /** Value is mutable for variable expansion */
    void setValue(String v);
    /** Indicates that variable expansion has occurred. */
    boolean isExpanded();
    void setExpanded(boolean v);
  }

  interface Nest extends Item {
    String getSectionName();
  }

  interface Unnest extends Item { }

  interface End extends Item { }

}
