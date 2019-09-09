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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.Cache2kManagerConfiguration;
import org.cache2k.impl.xmlConfiguration.generic.ParsedConfiguration;

import java.util.Map;

/**
 * Manager configuration context containing the default configuration and
 * global properties.
 *
 * @author Jens Wilke
 */
public class ConfigurationContext {

  private final Cache2kManagerConfiguration managerConfiguration = new Cache2kManagerConfiguration();
  private boolean configurationPresent = false;
  private ClassLoader classLoader;
  private Cache2kConfiguration<?, ?> defaultManagerConfiguration;
  private Map<String, String> predefinedSectionTypes;
  private ParsedConfiguration templates;

  public Cache2kConfiguration<?, ?> getDefaultManagerConfiguration() {
    return defaultManagerConfiguration;
  }

  public void setDefaultManagerConfiguration(final Cache2kConfiguration<?, ?> v) {
    defaultManagerConfiguration = v;
  }

  public boolean isConfigurationPresent() {
    return configurationPresent;
  }

  public void setConfigurationPresent(final boolean v) {
    configurationPresent = v;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public void setClassLoader(final ClassLoader v) {
    classLoader = v;
  }

  public Map<String, String> getPredefinedSectionTypes() {
    return predefinedSectionTypes;
  }

  public void setPredefinedSectionTypes(final Map<String, String> v) {
    predefinedSectionTypes = v;
  }

  public ParsedConfiguration getTemplates() {
    return templates;
  }

  public void setTemplates(final ParsedConfiguration v) {
    templates = v;
  }

  public Cache2kManagerConfiguration getManagerConfiguration() {
    return managerConfiguration;
  }

}
