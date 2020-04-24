package org.cache2k.impl.xmlConfiguration;

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

import org.cache2k.configuration.SingletonConfigurationSection;
import org.cache2k.impl.xmlConfiguration.generic.Section;
import org.cache2k.impl.xmlConfiguration.generic.Util;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author Jens Wilke
 */
public class CacheConfigBean<T> implements Section<T,T> {

  private Class<T> clazz;
  private String name;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public T createMutable() throws IllegalAccessException, InstantiationException {
    return clazz.newInstance();
  }

  @SuppressWarnings("unchecked")
  @Override
  public T mutableCopy(final T cfg) throws IOException, ClassNotFoundException {
    return (T) Util.copyViaSerialization((Serializable) cfg);
  }

  @Override
  public T build(final T mutableCfg) {
    return mutableCfg;
  }

  @Override
  public boolean isOccurringOnce() {
    return SingletonConfigurationSection.class.isAssignableFrom(clazz);
  }

  @Override
  public boolean isSupportingSubSections() {
    return false;
  }

  @Override
  public <S> void addSubSection(final T cfg, final S subSection) {
  }

  @Override
  public void validate(final T cfg) {
  }

}
