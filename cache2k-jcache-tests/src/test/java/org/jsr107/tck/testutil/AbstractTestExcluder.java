/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.testutil;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.logging.Logger;

/**
 * For the TCK we need to have an exclude list of bad tests so that disabling tests
 * can be done without changing code.
 * <p>
 * This class creates a rule for the class provided
 * </p>
 * The exclude list is created by {@link ExcludeList} by creating a file in the root of your classpath called
 * "ExcludeList". There is an example in the testRI module for testing the RI.
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public abstract class AbstractTestExcluder implements MethodRule {
  private final Logger logger = Logger.getLogger(getClass().getName());

  /**
   * {@inheritDoc}
   */
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    final String methodName = frameworkMethod.getName();
    final String className = frameworkMethod.getMethod().getDeclaringClass().getName();
    if (isExcluded(methodName)) {
      return new ExcludedStatement(className, methodName, logger);
    } else {
      return statement;
    }
  }

  protected abstract boolean isExcluded(String methodName);

  protected Logger getLogger() {
    return logger;
  }

  /**
   * Statement for excluded methods
   */
  private static final class ExcludedStatement extends Statement {
    private final String methodName;
    private final String className;
    private final Logger logger;

    private ExcludedStatement(String className, String methodName, Logger logger) {
      this.className = className;
      this.methodName = methodName;
      this.logger = logger;
    }

    @Override
    public void evaluate() throws Throwable {
      logger.info("===== EXCLUDING TEST '" + className + "'\t'" + methodName + "'.");
    }
  }
}
