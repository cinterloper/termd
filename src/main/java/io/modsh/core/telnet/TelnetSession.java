/*
 * Copyright 2014 Julien Viet
 *
 * Julien Viet licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
package io.modsh.core.telnet;

import io.modsh.core.io.BinaryDecoder;
import io.modsh.core.io.BinaryEncoder;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
*/
public class TelnetSession implements Consumer<byte[]> {

  static final byte BYTE_IAC = (byte)  0xFF;
  static final byte BYTE_DONT = (byte) 0xFE;
  static final byte BYTE_DO = (byte)   0xFD;
  static final byte BYTE_WONT = (byte) 0xFC;
  static final byte BYTE_WILL = (byte) 0xFB;
  static final byte BYTE_SB = (byte)   0xFA;
  static final byte BYTE_SE = (byte)   0xF0;
  static Charset UTF_8 = Charset.forName("UTF-8");
  Status status;
  HashMap<Byte, Boolean> options;
  Byte paramsOptionCode;
  byte[] paramsBuffer;
  int paramsLength;
  boolean paramsIac;
  BinaryDecoder decoder;
  BinaryEncoder encoder;
  final Consumer<byte[]> output;

  public TelnetSession(Consumer<byte[]> output) {
    this.status = Status.DATA;
    this.options = new HashMap<>();
    this.paramsOptionCode = null;
    this.paramsBuffer = null;
    this.paramsIac = false;
    this.encoder = null;
    this.decoder = null;
    this.output = output;
  }

  private void appendToParams(byte b) {
    while (paramsLength >= paramsBuffer.length) {
      paramsBuffer = Arrays.copyOf(paramsBuffer, paramsBuffer.length + 100);
    }
    paramsBuffer[paramsLength++] = b;
  }

  public void init() {
    onSendOptions();
    onOpen();
  }

  /**
   * Write a <i>do</i> option request to the client.
   *
   * @param option the option to send
   */
  public final void writeDoOption(Option option) {
    output.accept(new byte[]{BYTE_IAC, BYTE_DO, option.code});
  }

  /**
   * Write a do <i>will</i> request to the client.
   *
   * @param option the option to send
   */
  public final void writeWillOption(Option option) {
    output.accept(new byte[]{BYTE_IAC, BYTE_WILL, option.code});
  }

  private void rawWrite(byte[] data, int offset, int length) {
    if (length > 0) {
      if (offset == 0 && length == data.length) {
        output.accept(data);
      } else {
        byte[] chunk = new byte[length];
        System.arraycopy(data, offset, chunk, 0, chunk.length);
        output.accept(chunk);
      }
    }
  }

  /**
   * Write data to the client, escaping data if necessary or truncating it. The original buffer can
   * be mutated if incorrect data is provided.
   *
   * @param data the data to write
   */
  public final void write(byte[] data) {
    if (encoder != null) {
      int prev = 0;
      for (int i = 0;i < data.length;i++) {
        if (data[i] == -1) {
          rawWrite(data, prev, i - prev);
          output.accept(new byte[]{-1,-1});
          prev = i + 1;
        }
      }
      rawWrite(data, prev, data.length - prev);
    } else {
      for (int i = 0;i < data.length;i++) {
        data[i] = (byte)(data[i] & 0x7F);
      }
      output.accept(data);
    }
  }

  protected void onSendOptions() {
    writeWillOption(Option.ECHO);
    writeWillOption(Option.SGA);
    writeDoOption(Option.NAWS);
    writeDoOption(Option.BINARY);
    writeWillOption(Option.BINARY);
    writeDoOption(Option.TERMINAL_TYPE);
  }

  @Override
  public void accept(byte[] data) {
    for (int i = 0;i < data.length;i++) {
      status.handle(this, data[i]);
    }
  }

  protected void onByte(byte b) {
    if (decoder != null) {
      decoder.onByte(b);
    } else {
      onChar((char) b);
    }
  }

  public void close() {
    onClose();
  }

  protected void onOpen() {}
  protected void onClose() {}
  protected void onSize(int width, int height) {}
  protected void onTerminalType(String terminalType) {}
  protected void onCommand(byte command) {}
  protected void onNAWS(boolean naws) {}
  protected void onEcho(boolean echo) {}
  protected void onSGA(boolean sga) {}
  protected void onChar(int c) {}

  /**
   * Handle option <code>WILL</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleWill(TelnetSession)} method
   * otherwise a <code>DON'T</code> will be sent to the client.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionWill(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleWill(this);
        return;
      }
    }
    output.accept(new byte[]{BYTE_IAC,BYTE_DONT,optionCode});
  }

  /**
   * Handle option <code>WON'T</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleWont(TelnetSession)} method.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionWont(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleWont(this);
        return;
      }
    }
  }

  /**
   * Handle option <code>DO</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleDo(TelnetSession)} method
   * otherwise a <code>WON'T</code> will be sent to the client.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionDo(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleDo(this);
        return;
      }
    }
    output.accept(new byte[]{BYTE_IAC,BYTE_WONT,optionCode});
  }

  /**
   * Handle option <code>DON'T</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleDont(TelnetSession)} method.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionDont(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleDont(this);
        return;
      }
    }
  }

  /**
   * Handle option parameters call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleParameters(TelnetSession, byte[])} method.
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionParameters(byte optionCode, byte[] parameters) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleParameters(this, parameters);
        return;
      }
    }
  }

  enum Status {

    DATA() {
      @Override
      void handle(TelnetSession session, byte b) {
        if (b == BYTE_IAC) {
          session.status = session.decoder == null ? IAC : ESC;
        } else {
          if (session.decoder == null) {
            if ((b & 0x80) != 0) {
              System.out.println("Unimplemented " + b);
            } else {
              session.onByte(b);
            }
          } else {
            session.onByte(b);
          }
        }
      }
    },

    ESC() {
      @Override
      void handle(TelnetSession session, byte b) {
        if (b == BYTE_IAC) {
          session.onByte((byte) - 1);
        } else {
          IAC.handle(session, b);
        }
      }
    },

    IAC() {
      @Override
      void handle(TelnetSession session, byte b) {
        if (b == BYTE_DO) {
          session.status = DO;
        } else if (b == BYTE_DONT) {
          session.status = DONT;
        } else if (b == BYTE_WILL) {
          session.status = WILL;
        } else if (b == BYTE_WONT) {
          session.status = WONT;
        } else if (b == BYTE_SB) {
          session.paramsBuffer = new byte[100];
          session.paramsLength = 0;
          session.status = SB;
        } else {
          session.onCommand(b);
          session.status = DATA;
        }
      }
    },

    SB() {
      @Override
      void handle(TelnetSession session, byte b) {
        if (session.paramsOptionCode == null) {
          session.paramsOptionCode = b;
        } else {
          if (session.paramsIac) {
            session.paramsIac = false;
            if (b == BYTE_SE) {
              try {
                session.onOptionParameters(session.paramsOptionCode, Arrays.copyOf(session.paramsBuffer, session.paramsLength));
              } finally {
                session.paramsOptionCode = null;
                session.paramsBuffer = null;
                session.status = DATA;
              }
            } else if (b == BYTE_IAC) {
              session.appendToParams((byte) -1);
            }
          } else {
            if (b == BYTE_IAC) {
              session.paramsIac = true;
            } else {
              session.appendToParams(b);
            }
          }
        }
      }
    },

    DO() {
      @Override
      void handle(TelnetSession session, byte b) {
        try {
          session.onOptionDo(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    DONT() {
      @Override
      void handle(TelnetSession session, byte b) {
        try {
          session.onOptionDont(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    WILL() {
      @Override
      void handle(TelnetSession session, byte b) {
        try {
          session.onOptionWill(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    WONT() {
      @Override
      void handle(TelnetSession session, byte b) {
        try {
          session.onOptionWont(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    ;

    abstract void handle(TelnetSession session, byte b);
  }
}
