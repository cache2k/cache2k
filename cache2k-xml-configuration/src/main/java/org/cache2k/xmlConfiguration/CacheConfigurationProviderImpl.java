package org.cache2k.xmlConfiguration;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationWithSections;
import org.cache2k.core.spi.CacheConfigurationProvider;
import org.cache2k.core.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
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
  private PropertyParser propertyParser = new StandardPropertyParser();
  private TokenizerFactory tokenizerFactory = new FlexibleXmlTokenizerFactory();
  private volatile Map<Class<?>, BeanPropertyMutator> type2mutator = new HashMap<Class<?>, BeanPropertyMutator>();
  private volatile ConfigurationContext defaultManagerContext = null;
  private volatile Map<CacheManager, ConfigurationContext> manager2defaultConfig = new HashMap<CacheManager, ConfigurationContext>();
  private final Map<String, String> standardSectionTypes = new HashMap<String, String>();

  {
    standardSectionTypes.put("jcache", "org.cache2k.jcache.JCacheConfiguration");
  }

  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    synchronized (this) {
      defaultManagerContext = createContext(cl, null, DEFAULT_CONFIGURATION_FILE);
    }
    return defaultManagerContext.getDefaultManagerName();
  }

  @Override
  public Cache2kConfiguration getDefaultConfiguration(final CacheManager mgr) {
    Cache2kConfiguration cfg = getManagerContext(mgr).getDefaultManagerConfiguration();
    try {
      return copyViaSerialization(mgr, cfg);
    } catch (Exception ex) {
      throw new CacheMisconfigurationException(
        "Copying default cache configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  @Override
  public <K, V> void augmentConfiguration(final CacheManager mgr, final Cache2kConfiguration<K, V> cfg) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return;
    }
    final String _cacheName = cfg.getName();
    if (_cacheName == null) {
      if (ctx.isIgnoreAnonymousCache()) {
        return;
      }
      throw new CacheMisconfigurationException(
        "Cache name missing, cannot apply XML configuration. " +
        "Consider parameter: ignoreAnonymousCache");
    }
    ParsedConfiguration _parsedTop = readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration _parsedCache = null;
    ParsedConfiguration _section = extractCacheSection(_parsedTop);
    if (_section != null) { _parsedCache = _section.getSection(_cacheName); }
    if (_parsedCache == null) {
      if (ctx.isIgnoreMissingCacheConfiguration()) {
        return;
      }
      String _exceptionText =
        "Configuration for cache '" + _cacheName + "' is missing. " +
          "Consider parameter: ignoreMissingCacheConfiguration";
      throw new ConfigurationException(_exceptionText, _parsedTop.getSource());
    }
    apply(_parsedCache, extractTemplates(_parsedTop), cfg);
    cfg.setExternalConfigurationPresent(true);
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
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    return cfg;
  }

  private ParsedConfiguration extractCacheSection(ParsedConfiguration pc) {
    ParsedConfiguration _cachesSection = pc.getSection("caches");
    if (_cachesSection == null) {
      return null;
    }
   return _cachesSection;
  }

  private void checkCacheConfigurationOnStartup(final ParsedConfiguration pc) {
    ParsedConfiguration _cachesSection = pc.getSection("caches");
    if (_cachesSection == null) {
      return;
    }
    ParsedConfiguration _templates = extractTemplates(pc);
    for (ParsedConfiguration _cacheConfig : _cachesSection.getSections()) {
      apply(_cacheConfig, _templates, new Cache2kConfiguration());
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
      if (mgr.isDefaultManager() && defaultManagerContext != null) {
        ctx = defaultManagerContext;
      } else {
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
    ctx.setDefaultManagerName(_managerName);
    if (pc != null) {
      applyDefaultConfigurationIfPresent(pc, _defaultConfiguration);
      apply(pc, null, ctx);
      ctx.setConfigurationPresent(true);
      if (!ctx.isSkipCheckOnStartup()) {
        checkCacheConfigurationOnStartup(pc);
      }
    }
    ctx.setDefaultManagerConfiguration(_defaultConfiguration);
    return ctx;
  }

  private ParsedConfiguration readManagerConfigurationWithExceptionHandling(final ClassLoader cl, final String _fileName) {
    ParsedConfiguration pc;
    try {
      pc = readManagerConfiguration(cl, _fileName);
    } catch (CacheException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new CacheMisconfigurationException("Reading configuration for manager from '" + _fileName + "'", ex);
    }
    return pc;
  }

  private void applyDefaultConfigurationIfPresent(final ParsedConfiguration _pc,
                                                  final Cache2kConfiguration _defaultConfiguration) {
    ParsedConfiguration _defaults = _pc.getSection("defaults");
    if (_defaults != null) {
      _defaults = _defaults.getSection("cache");
    }
    if (_defaults != null) {
      apply(_defaults, extractTemplates(_pc), _defaultConfiguration);
    }
  }

  private static <T extends Serializable> T copyViaSerialization(CacheManager mgr, T obj) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
      oos.flush();
      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      return (T) new ObjectInputStream(bin).readObject();
    } catch (Exception ex) {
      throw new CacheMisconfigurationException(
        "Copying default configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  /** Set properties in configuration bean based on the parsed configuration. Called by unit test. */
  void apply(ParsedConfiguration _parsedCfg, ParsedConfiguration _templates, Object cfg) {
    ConfigurationTokenizer.Property _include = _parsedCfg.getPropertyMap().get("include");
    if (_include != null) {
      for (String _template : _include.getValue().split(",")) {
        ParsedConfiguration c2 = null;
        if (_templates != null) { c2 = _templates.getSection(_template); }
        if (c2 == null) {
          throw new ConfigurationException("Template not found \'" + _template + "\'", _include.getSource(), _include.getLineNumber());
        }
        apply(c2, _templates, cfg);
      }
    }
    applyPropertyValues(_parsedCfg, cfg);

    if (!(cfg instanceof ConfigurationWithSections)) {
      return;
    }
    ConfigurationWithSections _configurationWithSections = (ConfigurationWithSections) cfg;
    for(ParsedConfiguration _parsedSection : _parsedCfg.getSections()) {
      String _sectionType = standardSectionTypes.get(_parsedSection.getName());
      if (_sectionType == null) {
        _sectionType = _parsedSection.getType();
      }
      if (_sectionType == null) {
        throw new ConfigurationException("type missing or unknown", _parsedSection.getSource(), _parsedSection.getLineNumber());
      }
      Class<?> _type;
      try {
        _type = Class.forName(_sectionType);
      } catch (ClassNotFoundException ex) {
        throw new ConfigurationException(
          "class not found '" + _sectionType + "'", _parsedSection.getSource(), _parsedSection.getLineNumber());
      }
      String _containerElementName = _parsedSection.getContainer();
      if ("sections".equals(_containerElementName)) {
        handleSection(_type, _configurationWithSections, _parsedSection, _templates);
      } if (!handleBean(_type, cfg, _parsedSection, _templates)) {
      }
    }
  }

  private boolean handleBean(final Class<?> _type, final Object cfg, final ParsedConfiguration _parsedCfg, final ParsedConfiguration _templates) {
    String _containerName = _parsedCfg.getContainer();
    BeanPropertyMutator m = provideMutator(cfg.getClass());
    Class<?> _targetType = m.getType(_containerName);
    if (_targetType == null) {
     return false;
    }
    if (!_targetType.isAssignableFrom(_type)) {
      throw new ConfigurationException("Type mismatch, expected: '" + _targetType.getName() + "'", _parsedCfg.getSource(), _parsedCfg.getLineNumber());
    }
    Object _bean;
    try {
      _bean = _type.newInstance();
    } catch (Exception ex) {
      throw new ConfigurationException("Cannot instantiate bean: " + ex, _parsedCfg.getSource(), _parsedCfg.getLineNumber());
    }
    apply(_parsedCfg, _templates, _bean);
    try {
      m.mutate(cfg, _containerName, _bean);
    } catch (InvocationTargetException ex) {
      Throwable t =  ex.getTargetException();
      if (t instanceof IllegalArgumentException) {
        throw new ConfigurationException("Value '" + _bean + "' rejected: " + t.getMessage(), _parsedCfg.getSource(), _parsedCfg.getLineNumber());
      }
      throw new ConfigurationException("Setting property: " + ex, _parsedCfg.getSource(), _parsedCfg.getLineNumber());
    } catch (Exception ex) {
      throw new ConfigurationException("Setting property: " + ex, _parsedCfg.getSource(), _parsedCfg.getLineNumber());
    }
    return true;
  }

  private void handleSection(final Class<?> _type, final ConfigurationWithSections cfg, final ParsedConfiguration sc, final ParsedConfiguration _templates) {
    ConfigurationSection _sectionBean = cfg.getSections().getSection((Class<ConfigurationSection>) _type);
    if (_sectionBean == null) {
      try {
        _sectionBean = (ConfigurationSection)  _type.newInstance();
      } catch (Exception ex) {
        throw new ConfigurationException("Cannot instantiate section class: " + ex, sc.getSource(), sc.getLineNumber());
      }
      cfg.getSections().add(_sectionBean);
    }
    apply(sc, _templates, _sectionBean);
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
        throw new ConfigurationException("Unknown property '" + p.getName() + "'", p.getSource(), p.getLineNumber());
      }
      Object obj;
      try {
        obj = propertyParser.parse(_propertyType, p.getValue());
      } catch (Exception ex) {
        if (ex instanceof IllegalArgumentException) {
          throw new ConfigurationException("Value '" + p.getValue() + "' rejected: " + ex.getMessage(), p.getSource(), p.getLineNumber());
        }
        throw new ConfigurationException("Cannot parse property: " + ex, p.getSource(), p.getLineNumber());
      }
      mutateAndCatch(_bean, m, p, obj);
    }
  }

  private void mutateAndCatch(final Object cfg, final BeanPropertyMutator m, final ConfigurationTokenizer.Property p, final Object obj) {
    try {
      m.mutate(cfg, p.getName(), obj);
    } catch (InvocationTargetException ex) {
      Throwable t =  ex.getTargetException();
      if (t instanceof IllegalArgumentException) {
        throw new ConfigurationException("Value '" + p.getValue() + "' rejected: " + t.getMessage(), p.getSource(), p.getLineNumber());
      }
      throw new ConfigurationException("Setting property: " + ex, p.getSource(), p.getLineNumber());
    } catch (Exception ex) {
      throw new ConfigurationException("Setting property: " + ex, p.getSource(), p.getLineNumber());
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
