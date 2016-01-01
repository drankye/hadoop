// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbNameSystemSection extends Table {
  public static FbNameSystemSection getRootAsFbNameSystemSection(ByteBuffer _bb) { return getRootAsFbNameSystemSection(_bb, new FbNameSystemSection()); }
  public static FbNameSystemSection getRootAsFbNameSystemSection(ByteBuffer _bb, FbNameSystemSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbNameSystemSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long namespaceId() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long genstampV1() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long genstampV2() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long genstampV1Limit() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long lastAllocatedBlockId() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long transactionId() { int o = __offset(14); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long rollingUpgradeStartTime() { int o = __offset(16); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createFbNameSystemSection(FlatBufferBuilder builder,
      long namespaceId,
      long genstampV1,
      long genstampV2,
      long genstampV1Limit,
      long lastAllocatedBlockId,
      long transactionId,
      long rollingUpgradeStartTime) {
    builder.startObject(7);
    FbNameSystemSection.addRollingUpgradeStartTime(builder, rollingUpgradeStartTime);
    FbNameSystemSection.addTransactionId(builder, transactionId);
    FbNameSystemSection.addLastAllocatedBlockId(builder, lastAllocatedBlockId);
    FbNameSystemSection.addGenstampV1Limit(builder, genstampV1Limit);
    FbNameSystemSection.addGenstampV2(builder, genstampV2);
    FbNameSystemSection.addGenstampV1(builder, genstampV1);
    FbNameSystemSection.addNamespaceId(builder, namespaceId);
    return FbNameSystemSection.endFbNameSystemSection(builder);
  }

  public static void startFbNameSystemSection(FlatBufferBuilder builder) { builder.startObject(7); }
  public static void addNamespaceId(FlatBufferBuilder builder, long namespaceId) { builder.addInt(0, (int)(namespaceId & 0xFFFFFFFFL), 0); }
  public static void addGenstampV1(FlatBufferBuilder builder, long genstampV1) { builder.addLong(1, genstampV1, 0); }
  public static void addGenstampV2(FlatBufferBuilder builder, long genstampV2) { builder.addLong(2, genstampV2, 0); }
  public static void addGenstampV1Limit(FlatBufferBuilder builder, long genstampV1Limit) { builder.addLong(3, genstampV1Limit, 0); }
  public static void addLastAllocatedBlockId(FlatBufferBuilder builder, long lastAllocatedBlockId) { builder.addLong(4, lastAllocatedBlockId, 0); }
  public static void addTransactionId(FlatBufferBuilder builder, long transactionId) { builder.addLong(5, transactionId, 0); }
  public static void addRollingUpgradeStartTime(FlatBufferBuilder builder, long rollingUpgradeStartTime) { builder.addLong(6, rollingUpgradeStartTime, 0); }
  public static int endFbNameSystemSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbNameSystemSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

