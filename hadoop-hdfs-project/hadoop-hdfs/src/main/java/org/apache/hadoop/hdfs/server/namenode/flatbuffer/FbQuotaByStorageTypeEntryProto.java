// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbQuotaByStorageTypeEntryProto extends Table {
  public static FbQuotaByStorageTypeEntryProto getRootAsIntelQuotaByStorageTypeEntryProto(ByteBuffer _bb) { return getRootAsIntelQuotaByStorageTypeEntryProto(_bb, new FbQuotaByStorageTypeEntryProto()); }
  public static FbQuotaByStorageTypeEntryProto getRootAsIntelQuotaByStorageTypeEntryProto(ByteBuffer _bb, FbQuotaByStorageTypeEntryProto obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbQuotaByStorageTypeEntryProto __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public int storageType() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long quota() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static void startIntelQuotaByStorageTypeEntryProto(FlatBufferBuilder builder) { builder.startObject(2); }

  public static int createIntelQuotaByStorageTypeEntryProto(FlatBufferBuilder builder,
                                                            int storageType,
                                                            long quota) {
    builder.startObject(2);
    FbQuotaByStorageTypeEntryProto.addQuota(builder, quota);
    FbQuotaByStorageTypeEntryProto.addStorageType(builder, storageType);
    return FbQuotaByStorageTypeEntryProto.endIntelQuotaByStorageTypeEntryProto(builder);
  }
  public static void addStorageType(FlatBufferBuilder builder, int storageType) { builder.addInt(0, storageType, 0); }
  public static void addQuota(FlatBufferBuilder builder, long quota) { builder.addLong(1, quota, 0); }
  public static int endIntelQuotaByStorageTypeEntryProto(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelQuotaByStorageTypeEntryProtoBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

