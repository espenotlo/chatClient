package no.ntnu.datakomm.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        // TODO Step 1: implement this method
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables
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
        // TODO Step 4: implement this method
        // Hint: remember to check if connection is active
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
        // TODO Step 2: Implement this method
        // Hint: Remember to check if connection is active
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
        // TODO Step 2: implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
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
        // TODO Step 3: implement this method
        // Hint: Reuse sendCommand() method
        sendCommand("login " + username);
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public String[] refreshUserList() {
        // TODO Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
        String[] users = null;
        if(isConnectionActive()) {
            this.toServer.println("users");
            String line;
            ArrayList<String> userlist = new ArrayList<>();
            try {
                while (this.fromServer.ready() && (line = this.fromServer.readLine()) != null) {
                    userlist.add(line);
                    }
                users = userlist.toArray(String[]::new);
                for (ChatListener chatListener : this.listeners) {
                    chatListener.onUserList(users);
                }
            } catch (IOException e) {
                   e.printStackTrace();
            }
        }

        return users;
    }

    /**
     * Send a private message to a single recipient.
     .
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // TODO Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        return sendCommand("privmsg " + recipient + " " + message);
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        // TODO Step 8: Implement this method
        // Hint: Reuse sendCommand() method
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        String serverResponse = null;

        // TODO Step 3: Implement this method
        if (!connection.isClosed()) {
            if (isConnectionActive()) {
                try {
                    serverResponse = this.fromServer.readLine();
                } catch (SocketException e) {
                    disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // TODO Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
        // with the stream and hence the socket. Probably a good idea to close the socket in that case.

        return serverResponse;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            parseIncomingCommands();
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {
            // TODO Step 3: Implement this method
            // Hint: Reuse waitServerResponse() method
            // Hint: Have a switch-case (or other way) to check what type of response is received from the server
            // and act on it.
            // Hint: In Step 3 you need to handle only login-related responses.
            // Hint: In Step 3 reuse onLoginResult() method
            String serverResponse = waitServerResponse();
            if(serverResponse != null) {
                String[] commands = serverResponse.split(" ");
                switch (commands[0]) {
                    case "msg":
                        onMsgReceived(false, commands[1], parseMessage(commands));
                        break;
                    case "privmsg":
                        onMsgReceived(true, commands[1], parseMessage(commands));
                        break;
                    case "msgerr":
                        onMsgError(parseCommand(commands));
                        break;
                    case "cmderr":
                        onCmdError(parseCommand(commands));
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
                    default:
                        break;
                }
            }

            // TODO Step 5: update this method, handle user-list response from the server
            // Hint: In Step 5 reuse onUserList() method
            // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners

            // TODO Step 8: add support for incoming supported command list (type: supported)

        }
    }

    /**
     * Extracts the message text from an incoming message command.
     *
     * @param array the incoming message request
     * @return {@code String} message text
     */
    private String parseMessage(String[] array) {
        //List<String> strings = Arrays.asList(array);
        //strings.remove(0);
        //strings.remove(0);
        List<String> strings = new LinkedList<>(Arrays.asList(array));
        strings.remove(0);
        strings.remove(0);
        StringBuilder sb = new StringBuilder();
        strings.forEach(sb::append);
        return sb.toString();
    }

    /**
     * Extracts the message text from an incoming command.
     *
     * @param array the incoming command request
     * @return {@code String} command text
     */
    private String parseCommand(String[] array) {
        //Could not parse users with this type of list.
        //List<String> strings = Arrays.asList(array);
        //strings.remove(0);

        List<String> strings = new LinkedList<String>(Arrays.asList(array));
        strings.remove(0);
        StringBuilder sb = new StringBuilder();
        strings.forEach(s -> sb.append(s).append(" "));
        return sb.toString().trim();
    }


    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener
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
