package org.cache2k.impl.xmlConfiguration.generic;

/*
 * #%L
 * cache2k implementation
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
 * A configuration section is a Java object. This interface represents the
 * interaction with an object of that type. This way configuration objects
 * can be plain Java beans or immutable objects with builders.
 *
 * <p>This is currently unfinished.
 *
 * @param <M> the type of the mutable object, e.g. a builder
 * @param <T> the type of the immutable / build object
 *           identical to {@code M} if it is beans
 * @author Jens Wilke
 */
public interface Section<M, T> {

  String getName();

  M createMutable() throws Exception;

  /**
   * Return a mutable copy of the contents of this configuration object.
   */
  M mutableCopy(T cfg) throws Exception;
  T build(M mutableCfg);

  /**
   * This subsection is only present once in a section.
   */
  boolean isOccurringOnce();
  boolean isSupportingSubSections();
  <S extends Object> void addSubSection(M cfg, S subSection);
  void validate(T cfg);

}
