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
 * If the configuration bean has additional sub configuration beans, then it implements this
 * interface. Sections are essentially Java beans. The mechanism is intended to allow extension
 * modules to come with an additional configuration, but, at the same time, do not let the
 * core API depend on it.
 *
 * @author Jens Wilke
 * @see org.cache2k.Cache2kBuilder#with(SectionBuilder[])
 */
public interface ConfigWithSections {

  SectionContainer getSections();

}
