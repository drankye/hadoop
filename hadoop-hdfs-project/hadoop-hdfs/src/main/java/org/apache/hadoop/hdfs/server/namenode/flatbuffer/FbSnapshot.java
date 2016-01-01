// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbSnapshot extends Table {
  public static FbSnapshot getRootAsFbSnapshot(ByteBuffer _bb) { return getRootAsFbSnapshot(_bb, new FbSnapshot()); }
  public static FbSnapshot getRootAsFbSnapshot(ByteBuffer _bb, FbSnapshot obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbSnapshot __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long snapshotId() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public FbINode root() { return root(new FbINode()); }
  public FbINode root(FbINode obj) { int o = __offset(6); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }

  public static int createFbSnapshot(FlatBufferBuilder builder,
      long snapshotId,
      int root) {
    builder.startObject(2);
    FbSnapshot.addRoot(builder, root);
    FbSnapshot.addSnapshotId(builder, snapshotId);
    return FbSnapshot.endFbSnapshot(builder);
  }

  public static void startFbSnapshot(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addSnapshotId(FlatBufferBuilder builder, long snapshotId) { builder.addInt(0, (int)(snapshotId & 0xFFFFFFFFL), 0); }
  public static void addRoot(FlatBufferBuilder builder, int rootOffset) { builder.addOffset(1, rootOffset, 0); }
  public static int endFbSnapshot(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbSnapshotBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

