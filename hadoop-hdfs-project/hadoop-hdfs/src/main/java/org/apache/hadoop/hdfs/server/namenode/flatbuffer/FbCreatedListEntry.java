// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbCreatedListEntry extends Table {
  public static FbCreatedListEntry getRootAsIntelCreatedListEntry(ByteBuffer _bb) { return getRootAsIntelCreatedListEntry(_bb, new FbCreatedListEntry()); }
  public static FbCreatedListEntry getRootAsIntelCreatedListEntry(ByteBuffer _bb, FbCreatedListEntry obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbCreatedListEntry __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public String name() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }

  public static int createIntelCreatedListEntry(FlatBufferBuilder builder,
      int name) {
    builder.startObject(1);
    FbCreatedListEntry.addName(builder, name);
    return FbCreatedListEntry.endIntelCreatedListEntry(builder);
  }

  public static void startIntelCreatedListEntry(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(0, nameOffset, 0); }
  public static int endIntelCreatedListEntry(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelCreatedListEntryBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

