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
 * A configuration section. Additional sections can be added to the main configuration
 * via {@link Cache2kConfig#getSections()}. Each section might contain sections as well.
 * Each section type is allowed to appear only once in the configuration.
 *
 * @author Jens Wilke
 */
public interface ConfigSection
  <SELF extends ConfigSection<SELF, B>, B extends SectionBuilder<B, SELF>>
  extends ConfigBean<SELF, B> {

  B builder();

}
