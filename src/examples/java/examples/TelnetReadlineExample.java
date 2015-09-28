/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package examples;

import io.termd.core.readline.Keymap;
import io.termd.core.readline.Readline;
import io.termd.core.telnet.netty.NettyTelnetBootstrap;
import io.termd.core.term.Capability;
import io.termd.core.term.Device;
import io.termd.core.term.TermInfo;
import io.termd.core.tty.TtyEvent;
import io.termd.core.tty.TtyConnection;
import io.termd.core.util.Helper;
import io.termd.core.telnet.TelnetTtyConnection;
import io.termd.core.telnet.TelnetBootstrap;

import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A test class.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TelnetReadlineExample {

/*
  public static final Handler<ReadlineRequest> ECHO_HANDLER = new Handler<ReadlineRequest>() {
    @Override
    public void handle(final ReadlineRequest request) {
      if (request.requestCount() == 0) {
        request.write("Welcome sir\r\n\r\n% ").end();
      } else {
        request.eventHandler(new Handler<TermEvent>() {
          @Override
          public void handle(TermEvent event) {
            if (event instanceof TermEvent.Read) {
              request.write("key pressed " + Helper.fromCodePoints(((TermEvent.Read) event).getData()) + "\r\n");
            }
          }
        });
        new Thread() {
          @Override
          public void run() {
            new Thread() {
              @Override
              public void run() {
                try {
                  Thread.sleep(3000);
                } catch (InterruptedException e) {
                  e.printStackTrace();
                } finally {
                  request.write("You just typed :" + request.line());
                  request.write("\r\n% ").end();
                }
              }
            }.start();
          }
        }.start();
      }
    }
  };
*/

  public synchronized static void main(String[] args) throws Exception {
    new TelnetReadlineExample("localhost", 4000).start();
    TelnetReadlineExample.class.wait();
  }

  private final TelnetBootstrap telnet;

  public TelnetReadlineExample(String host, int port) {
    this(new NettyTelnetBootstrap(host, port));
  }

  public TelnetReadlineExample(TelnetBootstrap telnet) {
    this.telnet = telnet;
  }

  public static final Consumer<TtyConnection> READLINE = new Consumer<TtyConnection>() {
    @Override
    public void accept(final TtyConnection conn) {
      InputStream inputrc = Keymap.class.getResourceAsStream("inputrc");
      Keymap keymap = new Keymap(inputrc);
      Readline readline = new Readline(keymap);
      for (io.termd.core.readline.Function function : Helper.loadServices(Thread.currentThread().getContextClassLoader(), io.termd.core.readline.Function.class)) {
        readline.addFunction(function);
      }
      conn.setTermHandler(term -> {
        TermInfo info = TermInfo.defaultInfo();
        Device device = info.getDevice(term.toLowerCase());
        Integer maxColors = device.getFeature(Capability.max_colors);
//        StringBuilder msg = new StringBuilder("Your term is " + term + " and we found a description for it:\n");
//        for (Feature<?> feature : device.getFeatures()) {
//          Capability<?> capability = feature.capability();
//          msg.append(capability.name).append(" (").append(capability.description).
//              append(")").append("\n");
//        }
//        conn.write(msg.toString());
      });
      conn.write("Welcome sir\n\n");
      read(conn, readline);
    }

    class Task extends Thread implements BiConsumer<TtyEvent, Integer> {

      final TtyConnection conn;
      final Readline readline;
      final String line;
      volatile boolean sleeping;

      public Task(TtyConnection conn, Readline readline, String line) {
        this.conn = conn;
        this.readline = readline;
        this.line = line;
      }

      @Override
      public void accept(TtyEvent event, Integer cp) {
        System.out.println("event = " + event);
        switch (event) {
          case INTR:
            if (sleeping) {
              interrupt();
            }
        }
      }

      @Override
      public void run() {
        conn.write("Running " + line + "\n");
        conn.setEventHandler(this);
        sleeping = true;
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          conn.write("Interrupted\n");
        } finally {
          sleeping = false;
          conn.setEventHandler(null);
        }
        read(conn, readline);
      }
    }

    public void read(final TtyConnection conn, final Readline readline) {
      readline.readline(conn, "% ", line -> {
        Task task = new Task(conn, readline, line);
        task.start();
      }, completion -> {
        System.out.println("want to complete line=" + Helper.fromCodePoints(completion.line()) + ",prefix=" + Helper.fromCodePoints(completion.prefix()));
        completion.complete(Helper.toCodePoints("abdeef"), true);
      });
    }
  };

  public void start() {
    telnet.start(() -> new TelnetTtyConnection(READLINE));
  }
}