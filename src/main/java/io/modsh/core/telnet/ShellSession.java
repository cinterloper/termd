package io.modsh.core.telnet;

import io.modsh.core.io.BinaryDecoder;
import io.modsh.core.io.BinaryEncoder;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ShellSession extends TelnetSession {

  BinaryDecoder decoder;
  BinaryEncoder encoder;

  public ShellSession() {
  }

  @Override
  protected void onSendBinary(boolean binary) {
    super.onSendBinary(binary);
    if (binary) {
      encoder = new BinaryEncoder(TelnetSession.UTF_8, b -> write(new byte[]{b}));
    }
  }

  @Override
  protected void onReceiveBinary(boolean binary) {
    super.onReceiveBinary(binary);
    decoder = new BinaryDecoder(TelnetSession.UTF_8, (int c) -> onChar((char) c));
  }

  @Override
  protected void onByte(byte b) {
    if (decoder != null) {
      decoder.onByte(b);
    } else {
      onChar((char) b);
    }
  }

  @Override
  protected void onOpen() {
    writeWillOption(Option.ECHO);
    writeWillOption(Option.SGA);
    writeDoOption(Option.NAWS);
    writeDoOption(Option.BINARY);
    writeWillOption(Option.BINARY);
    writeDoOption(Option.TERMINAL_TYPE);
  }

  protected void onChar(int c) {}

}