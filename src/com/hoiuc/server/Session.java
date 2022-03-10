package com.hoiuc.server;

import com.hoiuc.assembly.Player;
import com.hoiuc.io.IMessageHandler;
import com.hoiuc.io.Message;
import com.hoiuc.io.Util;
import com.hoiuc.stream.Server;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.util.ArrayList;

public class Session extends Thread
{
    private static int baseId;
    private static final String KEY = "D";
    public int id;
    private boolean connected;
    private boolean getKeyComplete;
    private ArrayList<Message> sendDatas;
    private byte curR;
    private byte curW;
    protected Socket socket;
    protected DataInputStream dis;
    protected DataOutputStream dos;
    IMessageHandler messageHandler;
    private final Object LOCK;
    public Player player;
    private byte type;
    public byte zoomLevel;
    private boolean isGPS;
    private int width;
    private int height;
    private boolean isQwert;
    private boolean isTouch;
    private String plastfrom;
    private byte languageId;
    private int provider;
    private String agent;
    private Server server;
    public int version;
    public String ipv4;
    public int outdelay = 50;
    
    public Session(final Socket socket, final IMessageHandler handler) {
        this.ipv4 = null;
        this.connected = false;
        this.getKeyComplete = false;
        this.sendDatas = new ArrayList<Message>();
        this.LOCK = new Object();
        this.player = null;
        this.server = Server.gI();
        this.id = Session.baseId++;
        try {
            this.setSocket(socket);
            this.messageHandler = handler;
            this.connected = true;
        }
        catch (IOException ioEx) {
            ioEx.printStackTrace();
        }
    }
    
    private void setSocket(final Socket socket) throws IOException {
        this.socket = socket;
        if (socket != null) {
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
        }
    }
    
    @Override
    public void run() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (Session.this.connected) {
                        while (Session.this.connected && Session.this.sendDatas.size() > 0) {
                            final Message m = Session.this.sendDatas.remove(0);
                            if (m != null) {
                                Session.this.doSendMessage(m);
                            }
                        }
                        synchronized (Session.this.LOCK) {
                            try {
                                Session.this.LOCK.wait(10L);
                            }
                            catch (InterruptedException ex) {}
                        }
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        try {
            while (this.connected) {
                final Message message = this.readMessage();
                if (message == null) {
                    break;
                }
//                util.Debug(this + " do message " + message.getCommand() + " size " + message.reader().available());
                this.messageHandler.processMessage(this, message);
                message.cleanup();
            }
        }
        catch (Exception ex) {}
        this.disconnect();
        this.dis = null;
    }
    
    private Message readMessage() throws Exception {
        byte cmd = this.dis.readByte();
        if (cmd != -27) {
            cmd = this.readKey(cmd);
        }
        int size;
        if (cmd != -27) {
            final byte b1 = this.dis.readByte();
            final byte b2 = this.dis.readByte();
            size = ((this.readKey(b1) & 0xFF) << 8 | (this.readKey(b2) & 0xFF));
        }
        else {
            size = this.dis.readUnsignedShort();
        }
        final byte[] data = new byte[size];
        for (int len = 0, byteRead = 0; len != -1 && byteRead < size; byteRead += len) {
            len = this.dis.read(data, byteRead, size - byteRead);
            if (len > 0) {}
        }
        if (cmd != -27) {
            for (int i = 0; i < data.length; ++i) {
                data[i] = this.readKey(data[i]);
            }
        }
        final Message msg = new Message(cmd, data);
        return msg;
    }
    
    protected synchronized void doSendMessage(final Message m) throws IOException {
        try {
            final byte[] data = m.getData();
            if (data != null) {
                byte b = m.getCommand();
                final int size = data.length;
                if (size > 65535) {
                    b = -32;
                }
                if (this.getKeyComplete) {
                    this.dos.writeByte(this.writeKey(b));
                }
                else {
                    this.dos.writeByte(b);
                }
                if (b == -32) {
                    b = m.getCommand();
                    if (this.getKeyComplete) {
                        this.dos.writeByte(this.writeKey(b));
                    }
                    else {
                        this.dos.writeByte(b);
                    }
                    final int byte1 = this.writeKey((byte)(size >> 24));
                    this.dos.writeByte(byte1);
                    final int byte2 = this.writeKey((byte)(size >> 16));
                    this.dos.writeByte(byte2);
                    final int byte3 = this.writeKey((byte)(size >> 8));
                    this.dos.writeByte(byte3);
                    final int byte4 = this.writeKey((byte)(size & 0xFF));
                    this.dos.writeByte(byte4);
                }
                else if (this.getKeyComplete) {
                    final int byte1 = this.writeKey((byte)(size >> 8));
                    this.dos.writeByte(byte1);
                    final int byte2 = this.writeKey((byte)(size & 0xFF));
                    this.dos.writeByte(byte2);
                }
                else {
                    final int byte1 = (byte)(size & 0xFF00);
                    this.dos.writeByte(byte1);
                    final int byte2 = (byte)(size & 0xFF);
                    this.dos.writeByte(byte2);
                }
                if (this.getKeyComplete) {
                    for (int i = 0; i < size; ++i) {
                        data[i] = this.writeKey(data[i]);
                    }
                }
                this.dos.write(data);
//                util.Debug("do mss " + b + " size " + size);
            }
            this.dos.flush();
        }
        catch (IOException e) {
            this.disconnect();
            System.out.println("Error write message from client " + this.id);
        }
    }
    
    public void sendMessage(final Message m) {
        if (this.connected) {
            this.sendDatas.add(m);
        }
    }
    
    private byte readKey(final byte b) {
        final byte[] bytes = "D".getBytes();
        final byte curR = this.curR;
        this.curR = (byte)(curR + 1);
        final byte i = (byte)((bytes[curR] & 0xFF) ^ (b & 0xFF));
        if (this.curR >= "D".getBytes().length) {
            this.curR %= (byte)"D".getBytes().length;
        }
        return i;
    }
    
    private byte writeKey(final byte b) {
        final byte[] bytes = "D".getBytes();
        final byte curW = this.curW;
        this.curW = (byte)(curW + 1);
        final byte i = (byte)((bytes[curW] & 0xFF) ^ (b & 0xFF));
        if (this.curW >= "D".getBytes().length) {
            this.curW %= (byte)"D".getBytes().length;
        }
        return i;
    }
    
    public void hansakeMessage() throws IOException {
        final Message m = new Message(-27);
        m.writer().writeByte("D".getBytes().length);
        m.writer().writeByte("D".getBytes()[0]);
        for (int i = 1; i < "D".getBytes().length; ++i) {
            m.writer().writeByte("D".getBytes()[i] ^ "D".getBytes()[i - 1]);
        }
        m.writer().flush();
        this.doSendMessage(m);
        m.cleanup();
        this.getKeyComplete = true;
    }
    
    public void sendMessageLog(final String str) {
        try {
            final Message m = new Message(-26);
            m.writer().writeUTF(str);
            m.writer().flush();
            this.sendMessage(m);
            m.cleanup();
        }
        catch (IOException ex) {}
    }
    
    public boolean isConnected() {
        return this.connected;
    }
    
    @Override
    public String toString() {
        return "Conn:" + this.id;
    }
    
    public void setConnect(final Message m) throws IOException {
        this.type = m.reader().readByte();
        this.zoomLevel = m.reader().readByte();
        this.isGPS = m.reader().readBoolean();
        this.width = m.reader().readInt();
        this.height = m.reader().readInt();
        this.isQwert = m.reader().readBoolean();
        this.isTouch = m.reader().readBoolean();
        this.plastfrom = m.reader().readUTF();
        m.reader().readInt();
        m.reader().readByte();
        this.languageId = m.reader().readByte();
        this.provider = m.reader().readInt();
        this.agent = m.reader().readUTF();
        m.cleanup();
        Util.Debug("Connection type " + this.type + " zoomlevel " + this.zoomLevel + " width " + this.width + " height " + this.height);
    }
    
    public void loginGame(final Message m) throws Exception {
        final String uname = Util.strSQL(m.reader().readUTF());
        final String passw = Util.strSQL(m.reader().readUTF());
        final String version = m.reader().readUTF();
        this.version = Integer.parseInt(version.replace(".", ""));
        final String t1 = m.reader().readUTF();
        final String packages = m.reader().readUTF();
        final String random = m.reader().readUTF();
        final byte sv = m.reader().readByte();
        m.cleanup();
        final Player p = Player.login(this, uname, passw);
        if (p != null && (Util.CheckString(passw, "^[a-zA-Z0-9]+$"))) {
            this.player = p;
            outdelay = 0;
            server.manager.getPackMessage(p);
            HandleController.selectNinja(p, null);
            Util.Debug("Username: " + uname + " - Password: " + passw + " - Version: " + version);
//            server.manager.sendData(p);
//            server.manager.sendItem(p);
//            server.manager.sendSkill(p);
//            server.manager.sendMap(p);
        }
        else {
            if (!(Util.CheckString(passw, "^[a-zA-Z0-9]+$"))) {
                return;
            }
        }
    }
    
    public void disconnect() {
        if (!this.connected) {
            for (int jj = 0; jj < Server.arrayListIP.size(); jj++) {
                if (Server.arrayListIP.get(jj).equals(this.ipv4)) {
                    Server.arrayListIP.remove(jj);
                    break;
                }
            }
            return;
        }
        if (this.connected) {
            this.server.count_connect--;
            Session.baseId--;
            this.connected = false;
            System.out.println("Session:" + this.id + " disconnect");
            try {
                if (this.socket != null) {
                    this.socket.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            this.messageHandler.onDisconnected(this);
            synchronized (this.LOCK) {
                this.LOCK.notify();
            }
        }
    }
    
    static {
        Session.baseId = 0;
    }
}
