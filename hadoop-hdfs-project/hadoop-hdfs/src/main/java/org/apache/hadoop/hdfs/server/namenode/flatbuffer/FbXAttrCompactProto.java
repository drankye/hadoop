// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbXAttrCompactProto extends Table {
  public static FbXAttrCompactProto getRootAsIntelXAttrCompactProto(ByteBuffer _bb) { return getRootAsIntelXAttrCompactProto(_bb, new FbXAttrCompactProto()); }
  public static FbXAttrCompactProto getRootAsIntelXAttrCompactProto(ByteBuffer _bb, FbXAttrCompactProto obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbXAttrCompactProto __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long name() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public String value() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer valueAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }

  public static int createIntelXAttrCompactProto(FlatBufferBuilder builder,
      long name,
      int value) {
    builder.startObject(2);
    FbXAttrCompactProto.addValue(builder, value);
    FbXAttrCompactProto.addName(builder, name);
    return FbXAttrCompactProto.endIntelXAttrCompactProto(builder);
  }

  public static void startIntelXAttrCompactProto(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addName(FlatBufferBuilder builder, long name) { builder.addInt(0, (int)(name & 0xFFFFFFFFL), 0); }
  public static void addValue(FlatBufferBuilder builder, int valueOffset) { builder.addOffset(1, valueOffset, 0); }
  public static int endIntelXAttrCompactProto(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelXAttrCompactProtoBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

