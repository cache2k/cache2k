package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

/**
 * Special expiry calculator to calculate a separate expiry time if an
 * exceptions was thrown in the cache source. The general idea is that
 * depending on the nature of the exception (e.g. temporary or permanent)
 * a different expiry value can determined.
 *
 * @author Jens Wilke; created: 2014-10-16
 * @since 0.20
 * @deprecated replaced with {@link org.cache2k.customization.ExceptionExpiryCalculator}
 */
public interface ExceptionExpiryCalculator<K> extends org.cache2k.customization.ExceptionExpiryCalculator<K> {

  long calculateExpiryTime(K key, Throwable _throwable, long _fetchTime);

}
