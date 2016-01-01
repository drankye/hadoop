// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbFileDiff extends Table {
  public static FbFileDiff getRootAsFbFileDiff(ByteBuffer _bb) { return getRootAsFbFileDiff(_bb, new FbFileDiff()); }
  public static FbFileDiff getRootAsFbFileDiff(ByteBuffer _bb, FbFileDiff obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbFileDiff __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long snapshotId() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long fileSize() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String name() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }
  public FbINodeFile snapshotCopy() { return snapshotCopy(new FbINodeFile()); }
  public FbINodeFile snapshotCopy(FbINodeFile obj) { int o = __offset(10); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public FbBlockProto blocks(int j) { return blocks(new FbBlockProto(), j); }
  public FbBlockProto blocks(FbBlockProto obj, int j) { int o = __offset(12); return o != 0 ? obj.__init(__indirect(__vector(o) + j * 4), bb) : null; }
  public int blocksLength() { int o = __offset(12); return o != 0 ? __vector_len(o) : 0; }

  public static int createFbFileDiff(FlatBufferBuilder builder,
      long snapshotId,
      long fileSize,
      int name,
      int snapshotCopy,
      int blocks) {
    builder.startObject(5);
    FbFileDiff.addFileSize(builder, fileSize);
    FbFileDiff.addBlocks(builder, blocks);
    FbFileDiff.addSnapshotCopy(builder, snapshotCopy);
    FbFileDiff.addName(builder, name);
    FbFileDiff.addSnapshotId(builder, snapshotId);
    return FbFileDiff.endFbFileDiff(builder);
  }

  public static void startFbFileDiff(FlatBufferBuilder builder) { builder.startObject(5); }
  public static void addSnapshotId(FlatBufferBuilder builder, long snapshotId) { builder.addInt(0, (int)(snapshotId & 0xFFFFFFFFL), 0); }
  public static void addFileSize(FlatBufferBuilder builder, long fileSize) { builder.addLong(1, fileSize, 0); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(2, nameOffset, 0); }
  public static void addSnapshotCopy(FlatBufferBuilder builder, int snapshotCopyOffset) { builder.addOffset(3, snapshotCopyOffset, 0); }
  public static void addBlocks(FlatBufferBuilder builder, int blocksOffset) { builder.addOffset(4, blocksOffset, 0); }
  public static int createBlocksVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startBlocksVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endFbFileDiff(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbFileDiffBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

