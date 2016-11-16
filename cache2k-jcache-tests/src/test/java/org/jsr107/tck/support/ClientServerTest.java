package org.jsr107.tck.support;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertThat;

/**
 * Functional Tests for the {@link Client} and {@link Server} classes.
 *
 * @author Brian Oliver
 */
public class ClientServerTest {

  /**
   * Ensure that we and send a "ping" and receive a "pong" between a
   * {@link Client} and a {@link Server}.
   */
  @Test
  public void shouldPingPong() {

    Server server = new Server(10000);
    server.addOperationHandler(new PingPong());

    try {
      server.open();

      Client client = new Client(server.getInetAddress(), server.getPort());

      String result = client.invoke(new PingPong());

      assertThat(result, Matchers.equalTo("pong"));

    } catch (IOException e) {

    }
  }

  /**
   * The {@link PingPong} {@link Operation} and {@link OperationHandler}.
   */
  public static class PingPong implements Operation<String>, OperationHandler {
    @Override
    public String getType() {
      return "pingpong";
    }

    @Override
    public String onInvoke(ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
      oos.writeObject("ping");
      return (String) ois.readObject();
    }

    @Override
    public void onProcess(ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
      String request = (String) ois.readObject();
      oos.writeObject("pong");
    }
  }
}
