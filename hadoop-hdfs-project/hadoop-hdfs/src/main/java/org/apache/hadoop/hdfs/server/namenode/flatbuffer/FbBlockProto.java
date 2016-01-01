// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbBlockProto extends Table {
  public static FbBlockProto getRootAsFbBlockProto(ByteBuffer _bb) { return getRootAsFbBlockProto(_bb, new FbBlockProto()); }
  public static FbBlockProto getRootAsFbBlockProto(ByteBuffer _bb, FbBlockProto obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbBlockProto __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long blockId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long genStamp() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long numBytes() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createFbBlockProto(FlatBufferBuilder builder,
      long blockId,
      long genStamp,
      long numBytes) {
    builder.startObject(3);
    FbBlockProto.addNumBytes(builder, numBytes);
    FbBlockProto.addGenStamp(builder, genStamp);
    FbBlockProto.addBlockId(builder, blockId);
    return FbBlockProto.endFbBlockProto(builder);
  }

  public static void startFbBlockProto(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addBlockId(FlatBufferBuilder builder, long blockId) { builder.addLong(0, blockId, 0); }
  public static void addGenStamp(FlatBufferBuilder builder, long genStamp) { builder.addLong(1, genStamp, 0); }
  public static void addNumBytes(FlatBufferBuilder builder, long numBytes) { builder.addLong(2, numBytes, 0); }
  public static int endFbBlockProto(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbBlockProtoBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

