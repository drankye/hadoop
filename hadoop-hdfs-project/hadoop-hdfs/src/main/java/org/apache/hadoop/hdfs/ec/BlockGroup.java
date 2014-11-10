package org.apache.hadoop.hdfs.ec;

import java.util.ArrayList;
import java.util.List;

/**
 * In simple case like RS codec, only one SubBlockGroup;
 * in LRC codec, there're 3 SubBlockGroups, 2 local group plus 1 global group.
 */
public class BlockGroup {

  private List<SubBlockGroup> subGroups = new ArrayList<SubBlockGroup>();

  public List<SubBlockGroup> getSubGroups() {
    return subGroups;
  }

  public void addSubGroup(SubBlockGroup subGroup) {
    subGroups.add(subGroup);
  }
}
