// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeFile extends Table {
  public static FbINodeFile getRootAsFbINodeFile(ByteBuffer _bb) { return getRootAsFbINodeFile(_bb, new FbINodeFile()); }
  public static FbINodeFile getRootAsFbINodeFile(ByteBuffer _bb, FbINodeFile obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeFile __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long replication() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long modificationTime() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long accessTime() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long preferredBlockSize() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long permission() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public FbBlockProto blocks(int j) { return blocks(new FbBlockProto(), j); }
  public FbBlockProto blocks(FbBlockProto obj, int j) { int o = __offset(14); return o != 0 ? obj.__init(__indirect(__vector(o) + j * 4), bb) : null; }
  public int blocksLength() { int o = __offset(14); return o != 0 ? __vector_len(o) : 0; }
  public FbFileUnderConstructionFeature fileUC() { return fileUC(new FbFileUnderConstructionFeature()); }
  public FbFileUnderConstructionFeature fileUC(FbFileUnderConstructionFeature obj) { int o = __offset(16); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public FbAclFeatureProto acl() { return acl(new FbAclFeatureProto()); }
  public FbAclFeatureProto acl(FbAclFeatureProto obj) { int o = __offset(18); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public FbXAttrFeatureProto xAttrs() { return xAttrs(new FbXAttrFeatureProto()); }
  public FbXAttrFeatureProto xAttrs(FbXAttrFeatureProto obj) { int o = __offset(20); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public long storagePolicyID() { int o = __offset(22); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbINodeFile(FlatBufferBuilder builder,
      long replication,
      long modificationTime,
      long accessTime,
      long preferredBlockSize,
      long permission,
      int blocks,
      int fileUC,
      int acl,
      int xAttrs,
      long storagePolicyID) {
    builder.startObject(10);
    FbINodeFile.addPermission(builder, permission);
    FbINodeFile.addPreferredBlockSize(builder, preferredBlockSize);
    FbINodeFile.addAccessTime(builder, accessTime);
    FbINodeFile.addModificationTime(builder, modificationTime);
    FbINodeFile.addStoragePolicyID(builder, storagePolicyID);
    FbINodeFile.addXAttrs(builder, xAttrs);
    FbINodeFile.addAcl(builder, acl);
    FbINodeFile.addFileUC(builder, fileUC);
    FbINodeFile.addBlocks(builder, blocks);
    FbINodeFile.addReplication(builder, replication);
    return FbINodeFile.endFbINodeFile(builder);
  }

  public static void startFbINodeFile(FlatBufferBuilder builder) { builder.startObject(10); }
  public static void addReplication(FlatBufferBuilder builder, long replication) { builder.addInt(0, (int)(replication & 0xFFFFFFFFL), 0); }
  public static void addModificationTime(FlatBufferBuilder builder, long modificationTime) { builder.addLong(1, modificationTime, 0); }
  public static void addAccessTime(FlatBufferBuilder builder, long accessTime) { builder.addLong(2, accessTime, 0); }
  public static void addPreferredBlockSize(FlatBufferBuilder builder, long preferredBlockSize) { builder.addLong(3, preferredBlockSize, 0); }
  public static void addPermission(FlatBufferBuilder builder, long permission) { builder.addLong(4, permission, 0); }
  public static void addBlocks(FlatBufferBuilder builder, int blocksOffset) { builder.addOffset(5, blocksOffset, 0); }
  public static int createBlocksVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startBlocksVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static void addFileUC(FlatBufferBuilder builder, int fileUCOffset) { builder.addOffset(6, fileUCOffset, 0); }
  public static void addAcl(FlatBufferBuilder builder, int aclOffset) { builder.addOffset(7, aclOffset, 0); }
  public static void addXAttrs(FlatBufferBuilder builder, int xAttrsOffset) { builder.addOffset(8, xAttrsOffset, 0); }
  public static void addStoragePolicyID(FlatBufferBuilder builder, long storagePolicyID) { builder.addInt(9, (int)(storagePolicyID & 0xFFFFFFFFL), 0); }
  public static int endFbINodeFile(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeFileBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

