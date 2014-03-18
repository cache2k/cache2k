package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fetch time of next refresh from the value object itself. The value object
 * needs to implement {@link ValueWithNextRefreshTime}.
 *
 * @author Jens Wilke; created: 2013-05-02
 * @see ValueWithNextRefreshTime
 */
public final class ValueRefreshController<T> implements RefreshController<T> {

  public static final ValueRefreshController INSTANCE = new ValueRefreshController();

  @Override
  public long calculateNextRefreshTime(
    @Nullable T _oldObject,
    @Nonnull T _newObject,
    long _timeOfLastRefresh, long now) {
    return ((ValueWithNextRefreshTime) _newObject).getNextRefreshTime();
  }

}
