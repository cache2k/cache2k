package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * InputStream wrapper that reads directly from a bytebuffer.
 *
 * Credits: inspired by the stackoverflow question and adapted to my coding style.
 *
 * @author Jens Wilke; created: 2014-03-28
 */
public class ByteBufferInputStream extends InputStream {

  ByteBuffer byteBuffer;

  public ByteBufferInputStream(ByteBuffer buf) {
    byteBuffer = buf;
}

  public int read() throws IOException {
    if (!byteBuffer.hasRemaining()) {
      return -1;
    }
    return byteBuffer.get() & 0x0ff;
  }

  public int read(byte[] bytes, int off, int len) throws IOException {
    if (!byteBuffer.hasRemaining()) {
      return -1;
    }
    len = Math.min(len, byteBuffer.remaining());
    byteBuffer.get(bytes, off, len);
    return len;
  }

}
