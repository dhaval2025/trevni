package org.apache.trevni.input;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class InputMappedBuffer implements Input {

  private MappedByteBuffer mMBuf;
  private long length;

  public InputMappedBuffer(File file) throws IOException {
    FileChannel channel = new RandomAccessFile(file,"r").getChannel();
    mMBuf = channel.map(MapMode.READ_ONLY, 0, file.length());
    length = channel.size();
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public long length() throws IOException {
    return length;
  }

  @Override
  public int read(long position, byte[] b, int start, int len) throws IOException {
    mMBuf.position((int)position);
    mMBuf.get(b, (int)start, len);
    return b.length;
  }
}
