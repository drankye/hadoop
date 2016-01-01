// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeSymlink extends Table {
  public static FbINodeSymlink getRootAsFbINodeSymlink(ByteBuffer _bb) { return getRootAsFbINodeSymlink(_bb, new FbINodeSymlink()); }
  public static FbINodeSymlink getRootAsFbINodeSymlink(ByteBuffer _bb, FbINodeSymlink obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeSymlink __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long permission() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String target() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }

  public ByteBuffer targetAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public long modificationTime() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long accessTime() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createFbINodeSymlink(FlatBufferBuilder builder,
      long permission,
      int target,
      long modificationTime,
      long accessTime) {
    builder.startObject(4);
    FbINodeSymlink.addAccessTime(builder, accessTime);
    FbINodeSymlink.addModificationTime(builder, modificationTime);
    FbINodeSymlink.addPermission(builder, permission);
    FbINodeSymlink.addTarget(builder, target);
    return FbINodeSymlink.endFbINodeSymlink(builder);
  }

  public static void startFbINodeSymlink(FlatBufferBuilder builder) { builder.startObject(4); }
  public static void addPermission(FlatBufferBuilder builder, long permission) { builder.addLong(0, permission, 0); }
  public static void addTarget(FlatBufferBuilder builder, int targetOffset) { builder.addOffset(1, targetOffset, 0); }
  public static void addModificationTime(FlatBufferBuilder builder, long modificationTime) { builder.addLong(2, modificationTime, 0); }
  public static void addAccessTime(FlatBufferBuilder builder, long accessTime) { builder.addLong(3, accessTime, 0); }
  public static int endFbINodeSymlink(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeSymlinkBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

