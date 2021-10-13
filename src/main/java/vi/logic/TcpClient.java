package vi.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpClient {
    private final String host;
    private final int port;
    private Socket clientSocket;
    private PrintWriter outToServer;
    private BufferedReader inFromServer;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Try to establish TCP connection to the server (the three-way handshake).
     *
     * @return True when connection established, false on error
     */
    public boolean connectToServer() {
        try {
            this.clientSocket = new Socket(host, port);
            this.outToServer = new PrintWriter(this.clientSocket.getOutputStream(), true);
            this.inFromServer = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Closes the socket and the readers and writers,
     * effectively ending the connection.
     * @throws IOException if unable to close the reader or the socket.
     */
    public void stop() throws IOException {
        this.outToServer.close();
        this.inFromServer.close();
        this.clientSocket.close();
    }

    /**
     * Checks if the server is reachable.
     * @return {@code boolean} true if reachable, false if no connection to server.
     */
    public boolean pingServer() {
        try {
            return (this.clientSocket.getInetAddress().isReachable(100));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Wait for one response from the remote server.
     *
     * @return The response received from the server, null on error. The newline character is stripped away
     * (not included in the returned value).
     */
    public String readResponseFromServer() {
        try {
            if (!pingServer()) {
                return null;
            } else {
                return this.inFromServer.readLine();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Requests the server to log the user in with the given username.
     * @param name desired username.
     * @return {@code boolean} true if successful, false if not connected to server or username is already in use.
     */
    public boolean login(String name) {
        if (!this.clientSocket.isClosed()) {
            this.outToServer.println("login " + name);
            String response = readResponseFromServer();
            if (null != response) {
                return response.equals("loginok");
            }
        }
        return false;
    }

    //@TODO: Implement methods for the other server commands.
}
