package org.cache2k.extra.config.provider;

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

import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationWithSections;
import org.cache2k.configuration.SingletonConfigurationSection;
import org.cache2k.configuration.ValidatingConfigurationBean;
import org.cache2k.extra.config.generic.BeanPropertyMutator;
import org.cache2k.extra.config.generic.ConfigurationException;
import org.cache2k.extra.config.generic.ConfigurationTokenizer;
import org.cache2k.extra.config.generic.ParsedConfiguration;
import org.cache2k.extra.config.generic.PropertyParser;
import org.cache2k.extra.config.generic.SourceLocation;
import org.cache2k.extra.config.generic.StandardPropertyParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
public class ConfigurationProvider {
  private final PropertyParser propertyParser = new StandardPropertyParser();
  private volatile Map<Class<?>, BeanPropertyMutator> type2mutator =
    new HashMap<Class<?>, BeanPropertyMutator>();

  private static String constructGetterName(String containerName) {
    return "get" + Character.toUpperCase(containerName.charAt(0)) + containerName.substring(1);
  }

  /**
   * Set properties in configuration bean based on the parsed configuration.
   * Called by unit test.
   */
  void apply(ConfigurationContext ctx, ParsedConfiguration parsedCfg, Object cfg) {
    ParsedConfiguration templates = ctx.getTemplates();
    ConfigurationTokenizer.Property include = parsedCfg.getPropertyMap().get("include");
    if (include != null) {
      for (String template : include.getValue().split(",")) {
        ParsedConfiguration c2 = null;
        if (templates != null) { c2 = templates.getSection(template); }
        if (c2 == null) {
          throw new ConfigurationException("Template not found '" + template + "'", include);
        }
        apply(ctx, c2, cfg);
      }
    }
    applyPropertyValues(parsedCfg, cfg);
    if (!(cfg instanceof ConfigurationWithSections)) {
      return;
    }
    ConfigurationWithSections configurationWithSections = (ConfigurationWithSections) cfg;
    for (ParsedConfiguration parsedSection : parsedCfg.getSections()) {
      String sectionType = ctx.getPredefinedSectionTypes().get(parsedSection.getName());
      if (sectionType == null) {
        sectionType = parsedSection.getType();
      }
      if (sectionType == null) {
        throw new ConfigurationException("type missing or unknown", parsedSection);
      }
      Class<?> type;
      try {
        type = Class.forName(sectionType);
      } catch (ClassNotFoundException ex) {
        throw new ConfigurationException(
          "class not found '" + sectionType + "'", parsedSection);
      }
      if (!handleSection(ctx, type, configurationWithSections, parsedSection)
        && !handleCollection(ctx, type, cfg, parsedSection)
        && !handleBean(ctx, type, cfg, parsedSection)) {
        throw new ConfigurationException(
          "Unknown property  '" +  parsedSection.getContainer() + "'", parsedSection);
      }
    }
  }

  /**
   * Create the bean, apply configuration to it and set it.
   *
   * @return true, if applied, false if not a property
   */
  private boolean handleBean(
    ConfigurationContext ctx,
    Class<?> type,
    Object cfg,
    ParsedConfiguration parsedCfg) {
    String containerName = parsedCfg.getContainer();
    BeanPropertyMutator m = provideMutator(cfg.getClass());
    Class<?> targetType = m.getType(containerName);
    if (targetType == null) {
     return false;
    }
    if (!targetType.isAssignableFrom(type)) {
      throw new ConfigurationException(
        "Type mismatch, expected: '" + targetType.getName() + "'", parsedCfg);
    }
    Object bean = createBeanAndApplyConfiguration(ctx, type, parsedCfg);
    mutateAndCatch(cfg, m, containerName, bean, parsedCfg, bean);
    return true;
  }

  /**
   * Get appropriate collection e.g.
   * {@link org.cache2k.configuration.Cache2kConfiguration#getListeners()} create the bean
   * and add it to the collection.
   *
   * @return True, if applied, false if there is no getter for a collection.
   */
  @SuppressWarnings("unchecked")
  private boolean handleCollection(
    ConfigurationContext ctx,
    Class<?> type,
    Object cfg,
    ParsedConfiguration parsedCfg) {
    String containerName = parsedCfg.getContainer();
    Method m;
    try {
      m = cfg.getClass().getMethod(constructGetterName(containerName));
    } catch (NoSuchMethodException ex) {
      return false;
    }
    if (!Collection.class.isAssignableFrom(m.getReturnType())) {
      return false;
    }
    Collection c;
    try {
      c = (Collection) m.invoke(cfg);
    } catch (Exception ex) {
      throw new ConfigurationException(
        "Cannot access collection for '" + containerName + "' " + ex, parsedCfg);
    }
    Object bean = createBeanAndApplyConfiguration(ctx, type, parsedCfg);
    try {
      c.add(bean);
    } catch (IllegalArgumentException ex) {
      throw new ConfigurationException(
        "Rejected add '" + containerName + "': " + ex.getMessage(), parsedCfg);
    }
    return true;
  }

  private Object createBeanAndApplyConfiguration(
    ConfigurationContext ctx,
    Class<?> type,
    ParsedConfiguration parsedCfg) {
    Object bean;
    try {
      bean = type.newInstance();
    } catch (Exception ex) {
      throw new ConfigurationException("Cannot instantiate bean: " + ex, parsedCfg);
    }
    ParsedConfiguration parameters = parsedCfg.getSection("parameters");
    apply(ctx, parameters != null ? parameters : parsedCfg, bean);
    if (bean instanceof ValidatingConfigurationBean) {
      try {
        ((ValidatingConfigurationBean) bean).validate();
      } catch (IllegalArgumentException ex) {
        throw new ConfigurationException(
          "Validation error '" +
            bean.getClass().getSimpleName() + "': " + ex.getMessage(), parsedCfg);
      }
    }
    return bean;
  }

  /**
   * Create a new configuration section or reuse an existing section, if it is a singleton.
   *
   * <p>No support for writing on existing sections, which means it is not possible to define
   * a non singleton in the defaults section and then override values later in the cache specific
   * configuration. This is not needed for version 1.0. If we want to add it later, it is best to
   * use the name to select the correct section.
   *
   * @return true if applied, false if not a section.
   */
  private boolean handleSection(
    ConfigurationContext ctx,
    Class<?> type,
    ConfigurationWithSections cfg,
    ParsedConfiguration sc) {
    String containerName = sc.getContainer();
    if (!"sections".equals(containerName)) {
      return false;
    }
    @SuppressWarnings("unchecked") ConfigurationSection sectionBean =
      cfg.getSections().getSection((Class<ConfigurationSection>) type);
    if (!(sectionBean instanceof SingletonConfigurationSection)) {
      try {
        sectionBean = (ConfigurationSection)  type.newInstance();
      } catch (Exception ex) {
        throw new ConfigurationException("Cannot instantiate section class: " + ex, sc);
      }
      cfg.getSections().add(sectionBean);
    }
    apply(ctx, sc, sectionBean);
    return true;
  }

  private void applyPropertyValues(ParsedConfiguration cfg, Object bean) {
    BeanPropertyMutator m = provideMutator(bean.getClass());
    for (ConfigurationTokenizer.Property p : cfg.getPropertyMap().values()) {
      Class<?> propertyType = m.getType(p.getName());
      if (propertyType == null) {
        if ("include".equals(p.getName()) ||
          "name".equals(p.getName()) ||
          "type".equals(p.getName())) {
          continue;
        }
        throw new ConfigurationException("Unknown property '" + p.getName() + "'", p);
      }
      Object obj;
      try {
        obj = propertyParser.parse(propertyType, p.getValue());
      } catch (Exception ex) {
        if (ex instanceof NumberFormatException) {
          throw new ConfigurationException("Cannot parse number: '" + p.getValue() + "'", p);
        } else if (ex instanceof IllegalArgumentException) {
          throw new ConfigurationException(
            "Value '" + p.getValue() + "' parse error: " + ex.getMessage(), p);
        }
        throw new ConfigurationException("Cannot parse property: " + ex, p);
      }
      mutateAndCatch(bean, m, p, obj);
    }
  }

  private void mutateAndCatch(
    Object cfg,
    BeanPropertyMutator m,
    ConfigurationTokenizer.Property p,
    Object obj) {
    mutateAndCatch(cfg, m, p.getName(), p.getValue(), p, obj);
  }

  private void mutateAndCatch(
    Object cfg,
    BeanPropertyMutator m,
    String name,
    Object valueForExceptionText,
    SourceLocation loc,
    Object obj) {
    try {
      m.mutate(cfg, name, obj);
    } catch (InvocationTargetException ex) {
      Throwable t =  ex.getTargetException();
      if (t instanceof IllegalArgumentException) {
        throw new ConfigurationException(
          "Value '" + valueForExceptionText + "' rejected: " + t.getMessage(), loc);
      }
      throw new ConfigurationException("Setting property: " + ex, loc);
    } catch (Exception ex) {
      throw new ConfigurationException("Setting property: " + ex, loc);
    }
  }

  private BeanPropertyMutator provideMutator(Class<?> type) {
    BeanPropertyMutator m = type2mutator.get(type);
    if (m == null) {
      synchronized (this) {
        m = new BeanPropertyMutator(type);
        Map<Class<?>, BeanPropertyMutator> m2 =
          new HashMap<Class<?>, BeanPropertyMutator>(type2mutator);
        m2.put(type, m);
        type2mutator = m2;
      }
    }
    return m;
  }
}
