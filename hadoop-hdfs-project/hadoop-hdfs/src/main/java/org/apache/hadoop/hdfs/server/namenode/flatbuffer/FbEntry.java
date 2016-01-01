// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbEntry extends Table {
  public static FbEntry getRootAsIntelEntry(ByteBuffer _bb) { return getRootAsIntelEntry(_bb, new FbEntry()); }
  public static FbEntry getRootAsIntelEntry(ByteBuffer _bb, FbEntry obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbEntry __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long id() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public String str() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer strAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }

  public static int createIntelEntry(FlatBufferBuilder builder,
      long id,
      int str) {
    builder.startObject(2);
    FbEntry.addStr(builder, str);
    FbEntry.addId(builder, id);
    return FbEntry.endIntelEntry(builder);
  }

  public static void startIntelEntry(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addId(FlatBufferBuilder builder, long id) { builder.addInt(0, (int)(id & 0xFFFFFFFFL), 0); }
  public static void addStr(FlatBufferBuilder builder, int strOffset) { builder.addOffset(1, strOffset, 0); }
  public static int endIntelEntry(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelEntryBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

