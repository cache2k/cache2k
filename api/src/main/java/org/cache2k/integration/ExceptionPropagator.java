package org.cache2k.integration;

/*
 * #%L
 * cache2k API only package
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

/**
 * Exceptions from the {@link CacheLoader} are propagated via the {@link CacheLoaderException} by
 * default. It is possible to change this behaviour by registering a custom propagator.
 *
 * @author Jens Wilke; created: 2015-04-29
 */
public interface ExceptionPropagator {

  void propagateException(String _additionalMessage, Throwable _originalException);

}
