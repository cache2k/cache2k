package org.cache2k.extra.config.provider;

/*
 * #%L
 * cache2k config file support
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.config.BeanMarker;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheType;
import org.cache2k.config.ConfigBean;
import org.cache2k.config.ConfigWithSections;
import org.cache2k.config.ConfigSection;
import org.cache2k.config.ValidatingConfigBean;
import org.cache2k.extra.config.generic.BeanPropertyAccessor;
import org.cache2k.extra.config.generic.BeanPropertyMutator;
import org.cache2k.extra.config.generic.ConfigurationException;
import org.cache2k.extra.config.generic.ConfigurationTokenizer;
import org.cache2k.extra.config.generic.ParsedConfiguration;
import org.cache2k.extra.config.generic.PropertyParser;
import org.cache2k.extra.config.generic.SourceLocation;
import org.cache2k.extra.config.generic.StandardPropertyParser;
import org.cache2k.extra.config.generic.TargetPropertyAccessor;
import org.cache2k.extra.config.generic.TargetPropertyMutator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Jens Wilke
 */
public class ConfigurationProvider {
  private final PropertyParser propertyParser = new StandardPropertyParser();
  private final ConcurrentMap<Class<?>, TargetPropertyMutator> type2mutator =
    new ConcurrentHashMap<>();
  private final ConcurrentMap<Class<?>, TargetPropertyAccessor> type2accessor =
    new ConcurrentHashMap<>();

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
    if (!(cfg instanceof ConfigWithSections)) {
      return;
    }
    ConfigWithSections configurationWithSections = (ConfigWithSections) cfg;
    for (ParsedConfiguration parsedSection : parsedCfg.getSections()) {
      String sectionType = ctx.getPredefinedSectionTypes().get(parsedSection.getName());
      if (sectionType == null) {
        sectionType = parsedSection.getType();
      }
      if (sectionType == null) {
        throw new ConfigurationException("section type missing or unknown", parsedSection);
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
    TargetPropertyMutator m = provideMutator(cfg.getClass());
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
   * {@link Cache2kConfig#getListeners()} create the bean
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
    if (bean instanceof ValidatingConfigBean) {
      try {
        ((ValidatingConfigBean) bean).validate();
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
    ConfigWithSections cfg,
    ParsedConfiguration sc) {
    String containerName = sc.getContainer();
    if (!"sections".equals(containerName)) {
      return false;
    }
    @SuppressWarnings("unchecked") ConfigSection sectionBean =
      cfg.getSections().getSection((Class<ConfigSection>) type);
    if (!(sectionBean instanceof ConfigSection)) {
      try {
        sectionBean = (ConfigSection) type.newInstance();
      } catch (Exception ex) {
        throw new ConfigurationException("Cannot instantiate section class: " + ex, sc);
      }
      cfg.getSections().add(sectionBean);
    }
    apply(ctx, sc, sectionBean);
    return true;
  }

  private void applyPropertyValues(ParsedConfiguration cfg, Object bean) {
    TargetPropertyMutator m = provideMutator(bean.getClass());
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
    TargetPropertyMutator m,
    ConfigurationTokenizer.Property p,
    Object obj) {
    mutateAndCatch(cfg, m, p.getName(), p.getValue(), p, obj);
  }

  private void mutateAndCatch(
    Object cfg,
    TargetPropertyMutator m,
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

  private TargetPropertyMutator provideMutator(Class<?> type) {
    return
      type2mutator.computeIfAbsent(type, aClass -> new BeanPropertyMutator(aClass));
  }

  private TargetPropertyAccessor provideAccessor(Class<?> type) {
    return
      type2accessor.computeIfAbsent(type, aClass -> new BeanPropertyAccessor(aClass));
  }

  /**
   * Make a deep copy of the configuration object. This is used for copying
   * the default configuration.
   */
  @SuppressWarnings({"unchecked"})
  protected Object copy(Object cfg) {
    Class<?> targetType = cfg.getClass();
    if (!isBean(cfg)) {
      throw new UnsupportedOperationException("Cannot copy " + targetType.getName());
    }
    Object target;
    try {
      target = targetType.getConstructor().newInstance();
    } catch (Exception e) {
      throw new UnsupportedOperationException(
        "Bean needs default constructor " + targetType.getName());
    }
    TargetPropertyAccessor accessor = provideAccessor(targetType);
    TargetPropertyMutator mutator = provideMutator(targetType);
    for (String property : accessor.getNames()) {
      try {
        Object obj = accessor.access(cfg, property);
        if (obj == null || isImmutable(obj)) {
          mutator.mutate(target, property, obj);
        } else if (isCollection(obj)) {
          Collection<Object> targetCollection =
            (Collection<Object>) accessor.access(target, property);
          for (Object value : ((Collection<Object>) obj)) {
            targetCollection.add(copy(value));
          }
        } else {
          mutator.mutate(target, property, copy(obj));
        }
      } catch (Exception ex) {
        throw new RuntimeException(
          "Problem copying " + targetType.getName() + ":" + property, ex);
      }
    }
    return target;
  }

  private boolean isBean(Object obj) {
    return obj instanceof BeanMarker;
  }

  static final Set<Class> IMMUTABLE_TYPES = new HashSet<>(Arrays.asList(
    Boolean.class, Character.class, Integer.class, Long.class, Float.class, Double.class,
    String.class, Duration.class
  ));

  private boolean isImmutable(Object obj) {
    return IMMUTABLE_TYPES.contains(obj.getClass()) || obj instanceof CacheType;
  }

  private boolean isCollection(Object obj) {
    return obj instanceof Collection;
  }

}
