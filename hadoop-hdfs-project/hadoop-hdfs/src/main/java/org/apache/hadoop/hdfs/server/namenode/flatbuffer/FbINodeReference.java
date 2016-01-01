// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeReference extends Table {
  public static FbINodeReference getRootAsFbINodeReference(ByteBuffer _bb) { return getRootAsFbINodeReference(_bb, new FbINodeReference()); }
  public static FbINodeReference getRootAsFbINodeReference(ByteBuffer _bb, FbINodeReference obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeReference __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long referredId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String name() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public long dstSnapshotId() { int o = __offset(8); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long lastSnapshotId() { int o = __offset(10); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbINodeReference(FlatBufferBuilder builder,
      long referredId,
      int name,
      long dstSnapshotId,
      long lastSnapshotId) {
    builder.startObject(4);
    FbINodeReference.addReferredId(builder, referredId);
    FbINodeReference.addLastSnapshotId(builder, lastSnapshotId);
    FbINodeReference.addDstSnapshotId(builder, dstSnapshotId);
    FbINodeReference.addName(builder, name);
    return FbINodeReference.endFbINodeReference(builder);
  }

  public static void startFbINodeReference(FlatBufferBuilder builder) { builder.startObject(4); }
  public static void addReferredId(FlatBufferBuilder builder, long referredId) { builder.addLong(0, referredId, 0); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(1, nameOffset, 0); }
  public static void addDstSnapshotId(FlatBufferBuilder builder, long dstSnapshotId) { builder.addInt(2, (int)(dstSnapshotId & 0xFFFFFFFFL), 0); }
  public static void addLastSnapshotId(FlatBufferBuilder builder, long lastSnapshotId) { builder.addInt(3, (int)(lastSnapshotId & 0xFFFFFFFFL), 0); }
  public static int endFbINodeReference(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeReferenceBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

