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
 * Supports adding configuration sections via the builder pattern.
 * Sections are added via {@link org.cache2k.Cache2kBuilder#with}
 *
 * @author Jens Wilke
 */
public interface SectionBuilder
  <SELF extends SectionBuilder<SELF, T>,
    T extends ConfigSection<T, SELF>> extends ConfigBuilder<SELF, T> {

}
