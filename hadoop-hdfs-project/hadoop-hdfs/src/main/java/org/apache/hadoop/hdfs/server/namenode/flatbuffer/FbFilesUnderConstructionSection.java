// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbFilesUnderConstructionSection extends Table {
  public static FbFilesUnderConstructionSection getRootAsIntelFilesUnderConstructionSection(ByteBuffer _bb) { return getRootAsIntelFilesUnderConstructionSection(_bb, new FbFilesUnderConstructionSection()); }
  public static FbFilesUnderConstructionSection getRootAsIntelFilesUnderConstructionSection(ByteBuffer _bb, FbFilesUnderConstructionSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbFilesUnderConstructionSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }


  public static void startIntelFilesUnderConstructionSection(FlatBufferBuilder builder) { builder.startObject(0); }
  public static int endIntelFilesUnderConstructionSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelFilesUnderConstructionSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

