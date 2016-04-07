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
 * Simple parser interface to read in configuration files.
 *
 * @author Jens Wilke
 */
public interface ConfigPullParser {

  int END = 0;
  int PROPERTY = 1;
  int NEST = 2;
  int UNNEST = 3;

  String getPropertyName();

  String getPropertyValue();

  String getSectionName();

  int next() throws Exception;

}
