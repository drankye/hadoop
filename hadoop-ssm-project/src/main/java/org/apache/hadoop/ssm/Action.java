package org.apache.hadoop.ssm;

public enum Action {
  CACHE, ARCHIVE;

  public static Action getActionType(String str) {
    if (str.equals("cache"))
      return CACHE;
    else if (str.equals("archive"))
      return ARCHIVE;
    return null;
  }
}
