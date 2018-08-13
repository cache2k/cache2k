package org.cache2k.impl.xmlConfiguration;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationWithSections;
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.configuration.SingletonConfigurationSection;
import org.cache2k.configuration.ValidatingConfigurationBean;
import org.cache2k.core.spi.CacheConfigurationProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hooks into cache2k and provides the additional configuration data.
 *
 * @author Jens Wilke
 */
public class CacheConfigurationProviderImpl implements CacheConfigurationProvider {

  private static final String DEFAULT_CONFIGURATION_FILE = "cache2k.xml";
  private static final Map<String, String> version1SectionTypes = new HashMap<String, String>() {
    {
      put("jcache", "org.cache2k.jcache.JCacheConfiguration");
      put("byClassName", CustomizationSupplierByClassName.class.getName());
    }
  };

  private PropertyParser propertyParser = new StandardPropertyParser();
  private TokenizerFactory tokenizerFactory = new FlexibleXmlTokenizerFactory();
  private volatile Map<Class<?>, BeanPropertyMutator> type2mutator = new HashMap<Class<?>, BeanPropertyMutator>();
  private volatile Map<CacheManager, ConfigurationContext> manager2defaultConfig =
    new HashMap<CacheManager, ConfigurationContext>();
  private volatile Map<ClassLoader, ConfigurationContext> classLoader2config =
    new HashMap<ClassLoader, ConfigurationContext>();

  /**
   * The name of the default manager may be changed in the configuration file.
   * Load the default configuration file and save the loaded context for the respective
   * classloader, so we do not load the context twice when we create the first cache.
   */
  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    ConfigurationContext ctx = classLoader2config.get(cl);
    if (ctx == null) {
      ctx = createContext(cl, null, DEFAULT_CONFIGURATION_FILE);
      Map<ClassLoader, ConfigurationContext> m2 = new HashMap<ClassLoader, ConfigurationContext>(classLoader2config);
      m2.put(cl, ctx);
      classLoader2config = m2;
    }
    return ctx.getManagerConfiguration().getDefaultManagerName();
  }

  @Override
  public Cache2kConfiguration getDefaultConfiguration(final CacheManager mgr) {
    Cache2kConfiguration cfg = getManagerContext(mgr).getDefaultManagerConfiguration();
    return copyViaSerialization(mgr, cfg);
  }

  @Override
  public <K, V> void augmentConfiguration(final CacheManager mgr, final Cache2kConfiguration<K, V> cfg) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return;
    }
    final String _cacheName = cfg.getName();
    if (_cacheName == null) {
      if (ctx.getManagerConfiguration().isIgnoreAnonymousCache()) {
        return;
      }
      throw new ConfigurationException(
        "Cache name missing, cannot apply XML configuration. " +
        "Consider parameter: ignoreAnonymousCache");
    }
    ParsedConfiguration _parsedTop = readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration _section = extractCachesSection(_parsedTop);
    ParsedConfiguration _parsedCache = null;
    if (_section != null) { _parsedCache = _section.getSection(_cacheName); }
    if (_parsedCache == null) {
      if (ctx.getManagerConfiguration().isIgnoreMissingCacheConfiguration()) {
        return;
      }
      String _exceptionText =
        "Configuration for cache '" + _cacheName + "' is missing. " +
          "Consider parameter: ignoreMissingCacheConfiguration" +
          " at " + _parsedTop.getSource();
      throw new IllegalArgumentException(_exceptionText);
    }
    apply(ctx, _parsedCache, cfg);
  }

  @Override
  public Iterable<String> getConfiguredCacheNames(final CacheManager mgr) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return Collections.emptyList();
    }
    ParsedConfiguration _parsedTop = readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration _section = extractCachesSection(_parsedTop);
    if (_section == null) {
      return Collections.emptyList();
    }
    List<String> _names = new ArrayList<String>();
    for (ParsedConfiguration pc : _section.getSections()) {
      _names.add(pc.getName());
    }
    return _names;
  }

  private static String getFileName(CacheManager mgr) {
    if (mgr.isDefaultManager()) {
      return DEFAULT_CONFIGURATION_FILE;
    }
    return "cache2k-" + mgr.getName() + ".xml";
  }

  private ParsedConfiguration readManagerConfiguration(ClassLoader cl, final String _fileName) throws Exception {
    InputStream is = cl.getResourceAsStream(_fileName);
    if (is == null) {
      return null;
    }
    ConfigurationTokenizer tkn = tokenizerFactory.createTokenizer(_fileName, is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(tkn);
    is.close();
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    return cfg;
  }

  private ParsedConfiguration extractCachesSection(ParsedConfiguration pc) {
    ParsedConfiguration _cachesSection = pc.getSection("caches");
    if (_cachesSection == null) {
      return null;
    }
   return _cachesSection;
  }

  private void checkCacheConfigurationOnStartup(final ConfigurationContext ctx, final ParsedConfiguration pc) {
    ParsedConfiguration _cachesSection = pc.getSection("caches");
    if (_cachesSection == null) {
      return;
    }
    for (ParsedConfiguration _cacheConfig : _cachesSection.getSections()) {
      apply(ctx, _cacheConfig, new Cache2kConfiguration());
    }
  }

  /**
   * Hold the cache default configuration of a manager in a hash table. This is reused for all caches of
   * one manager.
   */
  private ConfigurationContext getManagerContext(final CacheManager mgr) {
    ConfigurationContext ctx = manager2defaultConfig.get(mgr);
    if (ctx != null) {
      return ctx;
    }
    synchronized (this) {
      ctx = manager2defaultConfig.get(mgr);
      if (ctx != null) {
        return ctx;
      }
      if (mgr.isDefaultManager()) {
        ctx = classLoader2config.get(mgr.getClassLoader());
      }
      if (ctx == null) {
        ctx = createContext(mgr.getClassLoader(), mgr.getName(), getFileName(mgr));
      }
      Map<CacheManager, ConfigurationContext> m2 =
        new HashMap<CacheManager, ConfigurationContext>(manager2defaultConfig);
      m2.put(mgr, ctx);
      manager2defaultConfig = m2;
      return ctx;
    }
  }

  private ConfigurationContext createContext(ClassLoader cl, String _managerName, String _fileName_fileName) {
    ParsedConfiguration pc = readManagerConfigurationWithExceptionHandling(cl, _fileName_fileName);
    ConfigurationContext ctx = new ConfigurationContext();
    ctx.setClassLoader(cl);
    Cache2kConfiguration _defaultConfiguration = new Cache2kConfiguration();
    ctx.setDefaultManagerConfiguration(_defaultConfiguration);
    ctx.getManagerConfiguration().setDefaultManagerName(_managerName);
    if (pc != null) {
      _defaultConfiguration.setExternalConfigurationPresent(true);
      ctx.setTemplates(extractTemplates(pc));
      apply(ctx, pc, ctx.getManagerConfiguration());
      if (ctx.getManagerConfiguration().getVersion() != null && ctx.getManagerConfiguration().getVersion().startsWith("1.")) {
        ctx.setPredefinedSectionTypes(version1SectionTypes);
      }
      applyDefaultConfigurationIfPresent(ctx, pc, _defaultConfiguration);
      ctx. setConfigurationPresent(true);
      if (!ctx.getManagerConfiguration().isSkipCheckOnStartup()) {
        checkCacheConfigurationOnStartup(ctx, pc);
      }
    }
    return ctx;
  }

  private ParsedConfiguration readManagerConfigurationWithExceptionHandling(final ClassLoader cl, final String _fileName) {
    ParsedConfiguration pc;
    try {
      pc = readManagerConfiguration(cl, _fileName);
    } catch (CacheException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ConfigurationException("Reading configuration for manager from '" + _fileName + "'", ex);
    }
    return pc;
  }

  private void applyDefaultConfigurationIfPresent(final ConfigurationContext ctx,
                                                  final ParsedConfiguration _pc,
                                                  final Cache2kConfiguration _defaultConfiguration) {
    ParsedConfiguration _defaults = _pc.getSection("defaults");
    if (_defaults != null) {
      _defaults = _defaults.getSection("cache");
    }
    if (_defaults != null) {
      apply(ctx, _defaults, _defaultConfiguration);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T copyViaSerialization(CacheManager mgr, T obj) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
      oos.flush();
      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      return (T) new ObjectInputStream(bin).readObject();
    } catch (Exception ex) {
      throw new ConfigurationException("Copying default cache configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  /** Set properties in configuration bean based on the parsed configuration. Called by unit test. */
  void apply(final ConfigurationContext ctx, final ParsedConfiguration _parsedCfg, final Object cfg) {
    ParsedConfiguration _templates = ctx.getTemplates();
    ConfigurationTokenizer.Property _include = _parsedCfg.getPropertyMap().get("include");
    if (_include != null) {
      for (String _template : _include.getValue().split(",")) {
        ParsedConfiguration c2 = null;
        if (_templates != null) { c2 = _templates.getSection(_template); }
        if (c2 == null) {
          throw new ConfigurationException("Template not found \'" + _template + "\'", _include);
        }
        apply(ctx, c2, cfg);
      }
    }
    applyPropertyValues(_parsedCfg, cfg);
    if (!(cfg instanceof ConfigurationWithSections)) {
      return;
    }
    ConfigurationWithSections _configurationWithSections = (ConfigurationWithSections) cfg;
    for(ParsedConfiguration _parsedSection : _parsedCfg.getSections()) {
      String _sectionType = ctx.getPredefinedSectionTypes().get(_parsedSection.getName());
      if (_sectionType == null) {
        _sectionType = _parsedSection.getType();
      }
      if (_sectionType == null) {
        throw new ConfigurationException("type missing or unknown", _parsedSection);
      }
      Class<?> _type;
      try {
        _type = Class.forName(_sectionType);
      } catch (ClassNotFoundException ex) {
        throw new ConfigurationException(
          "class not found '" + _sectionType + "'", _parsedSection);
      }
      if (!handleSection(ctx, _type, _configurationWithSections, _parsedSection)
        && !handleCollection(ctx, _type, cfg, _parsedSection)
        && !handleBean(ctx, _type, cfg, _parsedSection)) {
        throw new ConfigurationException("Unknown property  '" +  _parsedSection.getContainer() + "'", _parsedSection);
      }
    }
  }

  /**
   * Create the bean, apply configuration to it and set it.
   *
   * @return true, if applied, false if not a property
   */
  private boolean handleBean(
    final ConfigurationContext ctx,
    final Class<?> _type,
    final Object cfg,
    final ParsedConfiguration _parsedCfg) {
    String _containerName = _parsedCfg.getContainer();
    BeanPropertyMutator m = provideMutator(cfg.getClass());
    Class<?> _targetType = m.getType(_containerName);
    if (_targetType == null) {
     return false;
    }
    if (!_targetType.isAssignableFrom(_type)) {
      throw new ConfigurationException("Type mismatch, expected: '" + _targetType.getName() + "'", _parsedCfg);
    }
    Object _bean = createBeanAndApplyConfiguration(ctx, _type, _parsedCfg);
    mutateAndCatch(cfg, m, _containerName, _bean, _parsedCfg, _bean);
    return true;
  }

  /**
   * Get appropriate collection e.g. {@link Cache2kConfiguration#getListeners()} create the bean
   * and add it to the collection.
   *
   * @return True, if applied, false if there is no getter for a collection.
   */
  @SuppressWarnings("unchecked")
  private boolean handleCollection(
    final ConfigurationContext ctx,
    final Class<?> _type,
    final Object cfg,
    final ParsedConfiguration _parsedCfg) {
    String _containerName = _parsedCfg.getContainer();
    Method m;
    try {
      m = cfg.getClass().getMethod(constructGetterName(_containerName));
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
      throw new ConfigurationException("Cannot access collection for '" + _containerName + "' " + ex, _parsedCfg);
    }
    Object _bean = createBeanAndApplyConfiguration(ctx, _type, _parsedCfg);
    try {
      c.add(_bean);
    } catch (IllegalArgumentException ex) {
      throw new ConfigurationException("Rejected add '" + _containerName + "': " + ex.getMessage(), _parsedCfg);
    }
    return true;
  }

  private static String constructGetterName(final String _containerName) {
    return "get" + Character.toUpperCase(_containerName.charAt(0)) + _containerName.substring(1);
  }

  private Object createBeanAndApplyConfiguration(
    final ConfigurationContext ctx,
    final Class<?> _type,
    final ParsedConfiguration _parsedCfg) {
    Object _bean;
    try {
      _bean = _type.newInstance();
    } catch (Exception ex) {
      throw new ConfigurationException("Cannot instantiate bean: " + ex, _parsedCfg);
    }
    ParsedConfiguration _parameters = _parsedCfg.getSection("parameters");
    apply(ctx, _parameters != null ? _parameters : _parsedCfg, _bean);
    if (_bean instanceof ValidatingConfigurationBean) {
      try {
        ((ValidatingConfigurationBean) _bean).validate();
      } catch (IllegalArgumentException ex) {
        throw new ConfigurationException("Validation error '" + _bean.getClass().getSimpleName() +"': " + ex.getMessage(), _parsedCfg);
      }
    }
    return _bean;
  }

  /**
   * Create a new configuration section or reuse an existing section, if it is a singleton.
   *
   * <p>No support for writing on existing sections, which means it is not possible to define a non singleton
   * in the defaults section and then override values later in the cache specific configuration.
   * This is not needed for version 1.0. If we want to add it later, it is best to use the name to select the
   * correct section.
   *
   * @return true if applied, false if not a section.
   */
  private boolean handleSection(
    final ConfigurationContext ctx,
    final Class<?> _type,
    final ConfigurationWithSections cfg,
    final ParsedConfiguration sc) {
    String _containerName = sc.getContainer();
    if (!"sections".equals(_containerName)) {
      return false;
    }
    @SuppressWarnings("unchecked") ConfigurationSection _sectionBean = cfg.getSections().getSection((Class<ConfigurationSection>) _type);
    if (!(_sectionBean instanceof SingletonConfigurationSection)) {
      try {
        _sectionBean = (ConfigurationSection)  _type.newInstance();
      } catch (Exception ex) {
        throw new ConfigurationException("Cannot instantiate section class: " + ex, sc);
      }
      cfg.getSections().add(_sectionBean);
    }
    apply(ctx, sc, _sectionBean);
    return true;
  }

  private void applyPropertyValues(final ParsedConfiguration cfg, final Object _bean) {
    BeanPropertyMutator m = provideMutator(_bean.getClass());
    for (ConfigurationTokenizer.Property p : cfg.getPropertyMap().values()) {
      Class<?> _propertyType = m.getType(p.getName());
      if (_propertyType == null) {
        if ("include".equals(p.getName()) ||
          "name".equals(p.getName()) ||
          "type".equals(p.getName())) {
          continue;
        }
        throw new ConfigurationException("Unknown property '" + p.getName() + "'", p);
      }
      Object obj;
      try {
        obj = propertyParser.parse(_propertyType, p.getValue());
      } catch (Exception ex) {
        if (ex instanceof NumberFormatException) {
          throw new ConfigurationException("Cannot parse number: '" + p.getValue() + "'", p);
        } else if (ex instanceof IllegalArgumentException) {
          throw new ConfigurationException("Value '" + p.getValue() + "' parse error: " + ex.getMessage(), p);
        }
        throw new ConfigurationException("Cannot parse property: " + ex, p);
      }
      mutateAndCatch(_bean, m, p, obj);
    }
  }

  private void mutateAndCatch(
    final Object cfg,
    final BeanPropertyMutator m,
    final ConfigurationTokenizer.Property p,
    final Object obj) {
    mutateAndCatch(cfg, m, p.getName(), p.getValue(), p, obj);
  }

  private void mutateAndCatch(
    final Object cfg,
    final BeanPropertyMutator m,
    final String _name,
    final Object _valueForExceptionText,
    final SourceLocation loc,
    final Object obj) {
    try {
      m.mutate(cfg, _name, obj);
    } catch (InvocationTargetException ex) {
      Throwable t =  ex.getTargetException();
      if (t instanceof IllegalArgumentException) {
        throw new ConfigurationException("Value '" + _valueForExceptionText + "' rejected: " + t.getMessage(), loc);
      }
      throw new ConfigurationException("Setting property: " + ex, loc);
    } catch (Exception ex) {
      throw new ConfigurationException("Setting property: " + ex, loc);
    }
  }

  private BeanPropertyMutator provideMutator(Class<?> _type) {
    BeanPropertyMutator m = type2mutator.get(_type);
    if (m == null) {
      synchronized (this) {
        m = new BeanPropertyMutator(_type);
        Map<Class<?>, BeanPropertyMutator> m2 = new HashMap<Class<?>, BeanPropertyMutator>(type2mutator);
        m2.put(_type, m);
        type2mutator = m2;
      }
    }
    return m;
  }

  private ParsedConfiguration extractTemplates(final ParsedConfiguration _pc) {
    return _pc.getSection("templates");
  }

}
