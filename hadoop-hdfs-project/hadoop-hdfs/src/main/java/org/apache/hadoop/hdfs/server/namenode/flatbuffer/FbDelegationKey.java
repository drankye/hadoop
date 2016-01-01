// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbDelegationKey extends Table {
  public static FbDelegationKey getRootAsFbDelegationKey(ByteBuffer _bb) { return getRootAsFbDelegationKey(_bb, new FbDelegationKey()); }
  public static FbDelegationKey getRootAsFbDelegationKey(ByteBuffer _bb, FbDelegationKey obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbDelegationKey __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long id() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long expiryDate() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String key() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer keyAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }

  public static int createFbDelegationKey(FlatBufferBuilder builder,
      long id,
      long expiryDate,
      int key) {
    builder.startObject(3);
    FbDelegationKey.addExpiryDate(builder, expiryDate);
    FbDelegationKey.addKey(builder, key);
    FbDelegationKey.addId(builder, id);
    return FbDelegationKey.endFbDelegationKey(builder);
  }

  public static void startFbDelegationKey(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addId(FlatBufferBuilder builder, long id) { builder.addInt(0, (int)(id & 0xFFFFFFFFL), 0); }
  public static void addExpiryDate(FlatBufferBuilder builder, long expiryDate) { builder.addLong(1, expiryDate, 0); }
  public static void addKey(FlatBufferBuilder builder, int keyOffset) { builder.addOffset(2, keyOffset, 0); }
  public static int endFbDelegationKey(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbDelegationKeyBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

