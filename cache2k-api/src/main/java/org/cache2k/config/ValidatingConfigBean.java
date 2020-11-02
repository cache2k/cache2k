package org.cache2k.config;

/*
 * #%L
 * cache2k API
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
 * Configuration bean that is able to validate itself.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public interface ValidatingConfigBean extends ConfigBean {

  /**
   * Check whether the configuration is valid, especially whether mandatory fields
   * are set.
   *
   * @throws IllegalStateException if configuration is not valid
   */
  void validate();

}
