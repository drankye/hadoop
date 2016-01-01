// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbSection extends Table {
  public static FbSection getRootAsFbSection(ByteBuffer _bb) { return getRootAsFbSection(_bb, new FbSection()); }
  public static FbSection getRootAsFbSection(ByteBuffer _bb, FbSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public String name() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public long length() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long offset() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createFbSection(FlatBufferBuilder builder,
      int name,
      long length,
      long offset) {
    builder.startObject(3);
    FbSection.addOffset(builder, offset);
    FbSection.addLength(builder, length);
    FbSection.addName(builder, name);
    return FbSection.endFbSection(builder);
  }

  public static void startFbSection(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(0, nameOffset, 0); }
  public static void addLength(FlatBufferBuilder builder, long length) { builder.addLong(1, length, 0); }
  public static void addOffset(FlatBufferBuilder builder, long offset) { builder.addLong(2, offset, 0); }
  public static int endFbSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

