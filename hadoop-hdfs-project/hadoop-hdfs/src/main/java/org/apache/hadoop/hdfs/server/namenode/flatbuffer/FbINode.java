// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINode extends Table {
  public static FbINode getRootAsIntelINode(ByteBuffer _bb) { return getRootAsIntelINode(_bb, new FbINode()); }
  public static FbINode getRootAsIntelINode(ByteBuffer _bb, FbINode obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINode __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public int type() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long id() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String name() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }
  public FbINodeFile file() { return file(new FbINodeFile()); }
  public FbINodeFile file(FbINodeFile obj) { int o = __offset(10); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public FbINodeDirectory directory() { return directory(new FbINodeDirectory()); }
  public FbINodeDirectory directory(FbINodeDirectory obj) { int o = __offset(12); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public FbINodeSymlink symlink() { return symlink(new FbINodeSymlink()); }
  public FbINodeSymlink symlink(FbINodeSymlink obj) { int o = __offset(14); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }

  public static int createIntelINode(FlatBufferBuilder builder,
      int type,
      long id,
      int name,
      int file,
      int directory,
      int symlink) {
    builder.startObject(6);
    FbINode.addId(builder, id);
    FbINode.addSymlink(builder, symlink);
    FbINode.addDirectory(builder, directory);
    FbINode.addFile(builder, file);
    FbINode.addName(builder, name);
    FbINode.addType(builder, type);
    return FbINode.endIntelINode(builder);
  }

  public static void startIntelINode(FlatBufferBuilder builder) { builder.startObject(6); }
  public static void addType(FlatBufferBuilder builder, int type) { builder.addInt(0, type, 0); }
  public static void addId(FlatBufferBuilder builder, long id) { builder.addLong(1, id, 0); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(2, nameOffset, 0); }
  public static void addFile(FlatBufferBuilder builder, int fileOffset) { builder.addOffset(3, fileOffset, 0); }
  public static void addDirectory(FlatBufferBuilder builder, int directoryOffset) { builder.addOffset(4, directoryOffset, 0); }
  public static void addSymlink(FlatBufferBuilder builder, int symlinkOffset) { builder.addOffset(5, symlinkOffset, 0); }
  public static int endIntelINode(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelINodeBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

