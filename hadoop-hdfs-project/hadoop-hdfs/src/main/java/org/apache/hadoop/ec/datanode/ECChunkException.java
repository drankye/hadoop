package org.apache.hadoop.ec.datanode;

import java.io.IOException;

/**
 * Created by boli2 on 2014/12/10.
 */
public class ECChunkException extends Exception {

  static final long serialVersionUID = 2324534645656090155L;

  public ECChunkException() {
    super();
  }


  public ECChunkException(String message) {
    super(message);
  }

  public ECChunkException(String message, Throwable cause) {
    super(message, cause);
  }

  public ECChunkException(Throwable cause) {
    super(cause);
  }

}
