package com.hoiuc.stream;

import com.hoiuc.assembly.ClanManager;
import com.hoiuc.assembly.Map;
import com.hoiuc.io.SQLManager;
import com.hoiuc.io.Util;
import com.hoiuc.server.*;
import com.hoiuc.template.MapTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends Thread{
    private static Server instance = null;
    protected static ServerSocket listenSocket = null;
    public static boolean start = false;
    public static Manager manager;
    public static Menu menu;
    public static Controller serverMessageHandler;
    public static Map[] maps;
    public static Object LOCK_MYSQL = new Object();
    public static boolean running = true;
    public static RunTimeServer runTime = new RunTimeServer();
    public static Shinwa runShinwa = new Shinwa();
    public static ByteArrayOutputStream[] cache = new ByteArrayOutputStream[4];
    
    //đếm client
    public static ArrayList<String> arrayListIP = new ArrayList<>();
    public int count_connect;
    public static long delay;

    public static int baseId = 0;

    public Server() {
        this.listenSocket = null;
    }

    public static Server gI() {
        if (Server.instance == null) {
            Server.instance = new Server();
            Server.instance.init();
            Rank.init();
            Server.runTime.start();
            Server.runShinwa.start();
        }
        return Server.instance;
    }

    private synchronized void init() {
        manager = new Manager();
        menu = new Menu();
        serverMessageHandler = new Controller();
        cache[2] = GameSrc.loadFile("res/cache/skill");
        this.maps = new Map[MapTemplate.arrTemplate.length];
        short i;
        for (i = 0; i < this.maps.length; ++i) {
            this.maps[i] = new Map(i, null, null, null, null, null, null, null, null);
            this.maps[i].start();
        }

    }

    public static void start(boolean running) throws IOException {
        try {
            gI().start();
            listenSocket = new ServerSocket(Server.manager.post);
            System.out.println("Listen port: " + Server.manager.post);
            InetSocketAddress sockaddr;
            InetAddress inaddr;
            Inet4Address in4addr;
            String ip4string;
            //int idSer;
            start = running;
            Socket clientSocket;
            Session conn;
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("RUN HOOK");
                        for (Session conn : Client.gI().conns) {
                            if (conn != null && conn.player != null) {
                                conn.player.flush();
                            }
                        }
                        for (ClanManager entry : ClanManager.entrys) {
                            entry.flush();
                        }
                        System.out.println("CLOSE SERVER");
                        close(100L);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }));
            while (start) {
                if (delay < System.currentTimeMillis() && !Manager.isClearCloneLogin) {
                    clientSocket = listenSocket.accept();
                    conn = new Session(clientSocket, serverMessageHandler);
                    sockaddr = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
                    inaddr = sockaddr.getAddress();
                    in4addr = (Inet4Address) inaddr;
                    ip4string = in4addr.toString().substring(1);
                    Server.arrayListIP.add(ip4string);
                    if(Util.countIP(Server.arrayListIP, ip4string) <= Manager.max_connect_socket){
                        conn.ipv4 = ip4string;
                        conn.run();
                        Client.gI().put(conn);
                    }else{
                       conn.ipv4 = ip4string;                  
                       conn.disconnect();
                    }
                    delay = System.currentTimeMillis() + 500L;
                    System.out.println("Accept socket - " + conn.id + " - size :" + Client.gI().conns_size() + " - ip: " + ip4string);
                }
            }
            return;
        }
        
        catch (BindException bindEx) {
            System.exit(0);
        }
        catch (IOException genEx) {
            genEx.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void close(long delayKick) throws InterruptedException {
        if (Server.start) {
            Server.start = false;
            try {
                Server.listenSocket.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
            synchronized (Server.LOCK_MYSQL) {
                Server.LOCK_MYSQL.wait(delayKick);
            }
            synchronized (Server.LOCK_MYSQL) {
                Client.gI().Clear();
                ClanManager.close();
                ThienDiaBangManager.close();
                ShinwaManager.close();
            }
            Client.gI().close();
            Server.manager.close();
            Server.manager = null;
            Server.maps = null;
            Server.cache = null;
            Server.serverMessageHandler = null;
            Server.runTime = null;
            Server.runShinwa = null;
            Server.LOCK_MYSQL = null;
            SQLManager.close();
            System.gc();
        }
    }
}
