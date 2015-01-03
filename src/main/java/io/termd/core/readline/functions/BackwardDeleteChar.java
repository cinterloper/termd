package io.termd.core.readline.functions;

import io.termd.core.readline.Function;
import io.termd.core.readline.LineBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BackwardDeleteChar implements Function {

  @Override
  public String getName() {
    return "backward-delete-char";
  }

  @Override
  public void call(LineBuffer buffer) {
    buffer.deleteAt(-1);
  }
}