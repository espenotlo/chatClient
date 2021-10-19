package no.ntnu.datakomm.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

public class TCPClient {
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connection;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {
        boolean connected = false;

        try {
            this.connection = new Socket(host, port);
            connected = true;
            this.toServer = new PrintWriter(this.connection.getOutputStream(), true);
            this.fromServer = new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
            System.out.println("Connected");
        } catch (IOException e) {
            this.lastError = "Could not connect to server";
            System.err.println(this.lastError);
        }

        return connected;
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        if(isConnectionActive()) {
            try {
                this.connection.close();
                this.connection = null;
                onDisconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return connection != null;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {
        boolean messageSent = false;

        if(isConnectionActive()) {
            this.toServer.println(cmd);
            messageSent = true;
        } else {
            this.lastError = "Server is not connected, could not send message";
        }

        return messageSent;
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        boolean messageSent = false;

        String[] command = message.split(" ");
        if(command[0].equals("msg")) {
            messageSent = sendCommand(message);
        }

        return messageSent;
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {
        sendCommand("login " + username);
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        if(isConnectionActive()) {
            this.toServer.println("users");
            List<String> userlist;
            try {
                if (this.fromServer.ready()) {
                    userlist = Arrays.asList(this.fromServer.readLine().split(" "));
                    userlist.remove(0);

                    for (ChatListener chatListener : this.listeners) {
                        chatListener.onUserList(userlist.toArray(String[]::new));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a private message to a single recipient.
     .
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        return sendCommand("privmsg " + recipient + " " + message);
    }

    public void sendRequest(String request) {
        this.toServer.println(request);
    }

    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        if(isConnectionActive()) {
            this.toServer.println("help");
        }
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        String serverResponse = null;

        if (isConnectionActive() && !connection.isClosed()) {
            try {
                serverResponse = this.fromServer.readLine();
            } catch (SocketException e) {
                disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return serverResponse;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        return Objects.requireNonNullElse(lastError, "");
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(this::parseIncomingCommands);
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {
            String serverResponse = waitServerResponse();
            if(serverResponse != null) {
                String[] commands = serverResponse.split(" ");
                switch (commands[0]) {
                    case "msg":
                        onMsgReceived(false, commands[1], parseCommand(commands));
                        break;
                    case "privmsg":
                        onMsgReceived(true, commands[1], parseCommand(commands));
                        break;

                    case "inbox":
                        onMsgReceived(false,"",parseCommand(commands));
                        break;
                    case "msgok" :
                        //do nothing
                        break;
                    case "msgerr":
                        onMsgError(parseCommand(commands));
                        break;
                    case "loginok":
                        onLoginResult(true, "");
                        break;
                    case "loginerr":
                        onLoginResult(false, parseCommand(commands));
                        break;
                    case "supported":
                        onSupported(parseCommand(commands).split(" "));
                        break;
                    case "users":
                        onUsersList(parseCommand(commands).split(" "));
                        break;
                    case "cmderr":
                    default:
                        onCmdError(parseCommand(commands));
                }
            }
        }
    }

    /**
     * Extracts the message text from an incoming command.
     *
     * @param array the incoming command request
     * @return {@code String} command text
     */
    private String parseCommand(String[] array) {
        List<String> strings = new LinkedList<>(Arrays.asList(array));
        if (strings.get(0).equals("privmsg") || strings.get(0).equals("msg")) {
            strings.remove(0);
            strings.remove(0);
        } else {
            strings.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        strings.forEach(s -> sb.append(s).append(" "));
        return sb.toString().trim();
    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener the listener to be registered.
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener the listener to be removed.
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        for(ChatListener l : this.listeners) {
            l.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for(ChatListener l : this.listeners) {
            l.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        for(ChatListener l : this.listeners) {
            l.onMessageReceived(new TextMessage(sender, priv, text));
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        for(ChatListener l : this.listeners) {
            l.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        for(ChatListener l : this.listeners) {
            l.onCommandError(errMsg);
        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        for(ChatListener l : this.listeners) {
            l.onSupportedCommands(commands);
        }
    }
}
