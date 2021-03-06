
package UDP;

import Componenets.User;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

/**
 *
 * @author Ofir Attia
 */
public class RoomListener implements Runnable {

    Socket connection = null;
    final static String INET_ADDR = "224.0.0.3";

    /**
     * MAX SIZE OF PACKET
     */
    public static final int BUFSIZE = 4096;
    /**
     * socket of this server
     */
    private DatagramSocket socket;
    /**
     * @deprecated 
     */
    private DatagramSocket serverSocket;
    /**
     * address of this server
     */
    InetAddress addr;
    /**
     * data in of the server ( input stream )
     */
    DataInputStream dataIn = null;
    /**
     * output stream of the server
     */
    DataOutputStream dataOut = null;
    /**
     * jText object to write log to the gui
     */
    JTextArea jText;

    /**
     * jTree used to update the clients with this object
     */
    public JTree jTree;
    /**
     * current room listener
     */
    int roomNumber;
    /**
     * Room node tree
     */
    DefaultMutableTreeNode Room;

    /**
     * port of this server listen
     */
    public int port = 0;
    /**
     * hold the user list ( list of nodes )
     */
    private final List<User> userList = new ArrayList();
    /**
     * hold unique list of sockets - for broadcasting
     */
    HashMap<Integer, InetAddress> map = new HashMap<>();
    HashMap<Integer, String> Groups = new HashMap<>();
    /**
     *
     * @param port
     * @param jText
     * @param jTree
     * @param roomNumber
     * @param Room
     * @throws Exception
     */
    public RoomListener(int port, JTextArea jText, JTree jTree, int roomNumber, DefaultMutableTreeNode Room) throws Exception {
        try {
            this.port = port;
            // Bind to the specified UDP port, to listen
            // for incoming data packets
            socket = new DatagramSocket(this.port);
            serverSocket = new DatagramSocket();
            addr = InetAddress.getByName(INET_ADDR);
            System.out.println("Server active on port " + socket.getLocalPort());
        } catch (Exception e) {
            System.err.println("Unable to bind port");
        }

    }

    /**
     * broadcasting the packets to each user in the room
     */
    public void serviceClients() throws DataFormatException {

        // Create a buffer large enough for incoming packets
        byte[] buffer = new byte[BUFSIZE];

        for (;;) {
            try {
                // Create a DatagramPacket for reading UDP packets
                DatagramPacket packet = new DatagramPacket(buffer, BUFSIZE);
                // Receive incoming packet
                socket.receive(packet);
                // update sockets 
                map.put(packet.getPort(), packet.getAddress());
                System.out.println("Add client: "+packet.getPort()+" to users list");

                //byte[] unCompressed = decompress(packet.getData());
                byte[] group = Arrays.copyOfRange(packet.getData(), 0,2);
                byte[] p = Arrays.copyOfRange(packet.getData(), 2,packet.getData().length-1);
                
                System.out.println("String Group : "+new String(group).toString());
                String groupString = new String(group).toString();
                Groups.put(packet.getPort(), groupString);
                System.out.println("Add client: "+packet.getPort()+" to group : "+groupString);
                // broadcasting the packet
                 
                broad(p,packet.getPort(),groupString);
            } catch (IOException ioe) {
                System.err.println("Error : " + ioe);
            }
        }

    }

    @Override
    public synchronized void run() {

        try {
            serviceClients();
        } catch (DataFormatException ex) {
            //Logger.getLogger(RoomListener.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    /**
     * print the jTree for debugging
     * @param model
     * @param object
     * @param indent
     * @return 
     */
    private static String getTreeText(TreeModel model, Object object, String indent) {
        String myRow = indent + object + "\n";
        for (int i = 0; i < model.getChildCount(object); i++) {
            myRow += getTreeText(model, model.getChild(object, i), indent + "  ");
        }
        return myRow;
    }

    /**
     * @deprecated 
     * @param connection
     */
    public synchronized void removeUser(Socket connection) {

        int i = 0;
        String tempKey = getUserKey(connection);
        for (User user : userList) {

            if (user.toString().equals(tempKey)) {

                userList.remove(i);
                Room.remove(user);
                System.out.println("Removing user " + user + " from Room " + this.roomNumber);
                refreshTree();
                return;
            }
            i += 1;
        }

    }

    private synchronized void refreshTree() {
        DefaultTreeModel model = (DefaultTreeModel) this.jTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        model.nodeChanged(root);
        model.reload(root);

    }

    private synchronized void addUser(Socket connection) {
        User u1 = new User(getUserKey(connection));
        System.err.println(u1);
        userList.add(u1);
        Room.add(u1);
        refreshTree();

    }

    private synchronized String getUserKey(Socket connection) {
        return "U:" + connection.getInetAddress() + ":" + connection.getPort();
    }

    private void broad(DatagramPacket packet) {
        System.out.println("Broadcast");
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            try {
                HashMap.Entry pair = (HashMap.Entry) it.next();
                DatagramPacket msgPacket = new DatagramPacket(packet.getData(),
                        packet.getLength(), (InetAddress) pair.getValue(), (Integer) pair.getKey());
                System.out.println("prepare packet from : "+packet.getAddress() +" and port: "+packet.getPort()+" size of packet: "+packet.getLength());

                if (packet.getPort() == (Integer) pair.getKey()) {
                    System.out.println("packet of this socket, continue");
                    continue;
                }
                this.socket.send(msgPacket);
                //serverSocket.send(msgPacket);
                System.out.println("Send packet to: "+((Integer)pair.getKey()).toString()+" and port: "+((InetAddress)pair.getValue()).toString());
            } catch (IOException ex) {
                Logger.getLogger(RoomListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
    private void broad(byte[] packet,int port,String group) {
        System.out.println("Broadcast");
        Iterator it = Groups.entrySet().iterator();
        while (it.hasNext()) {
            try {
                
                HashMap.Entry pair = (HashMap.Entry) it.next();
                if(!pair.getValue().equals(group)){
                    System.out.println("Groups not the same: "+pair.getValue().toString()+ " and current group is: "+group);
                    continue;
                }
                
                InetAddress clientDataInetAddress =(InetAddress) map.get(pair.getKey());
                DatagramPacket msgPacket = new DatagramPacket(packet,
                        packet.length, clientDataInetAddress, (Integer) pair.getKey());
                //System.out.println("prepare packet from : "+packet.getAddress() +" and port: "+packet.getPort()+" size of packet: "+packet.getLength());

                if (((Integer)port).equals((Integer) pair.getKey())) {
                    System.out.println("packet of this socket, continue");
                    continue;
                }
                this.socket.send(msgPacket);
                //serverSocket.send(msgPacket);
                System.out.println("Send packet to: "+((Integer)pair.getKey()).toString()+" and port: "+(clientDataInetAddress).toString()+" in group : "+pair.getValue());
            } catch (IOException ex) {
                Logger.getLogger(RoomListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
