package org.mortbay.ijetty;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServer {
    //客户端列表
    private List<Socket> mList = new ArrayList<Socket>();
    //服务器
    private ServerSocket server = null;
    //线程池
    private ExecutorService mExecutorService = null; //thread pool

    public SocketServer(int port,IJetty iJetty) throws IOException {
            server = new ServerSocket(port);

            mExecutorService = Executors.newCachedThreadPool();  //create a thread pool

            Socket client = null;

            while(true) {
                client = server.accept();
                //把客户端放入客户端集合中
                mList.add(client);
                mExecutorService.execute(new Service(client,iJetty)); //start a new thread to handle the connection
            }

    }

    class Service implements Runnable {
        //客户端socket
        private Socket socket;
        //客户端输入流
        private BufferedReader in = null;
        //
        private String msg = null;

        private IJetty iJetty =null;

        public Service(Socket socket,IJetty iJetty) {
            this.socket = socket;
            this.iJetty = iJetty;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                客户端只要一连到服务器，便向客户端发送下面的信息。表示有客户端连上后，向所有客户端发送此信息
//                msg = "服务器地址:" +this.socket.getInetAddress() + "come total:"+mList.size()+"(服务器发送)";
//                this.writerALLClientMsg(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while(true) {
                    if(in!=null) {
                        if ((msg = in.readLine()) != null) {
                            if (msg.equals("exit")) {
                                mList.remove(socket);
                                socket.close();
                            } else if (msg.equals("status")) {
                                String msg = iJetty.getStatus();
                                writerClientMsg(msg, socket);
                                mList.remove(socket);
                                socket.close();
                            } else if (msg.equals("startVPN")) {
                                iJetty.startVPN();
                                mList.remove(socket);
                                socket.close();
                            } else if (msg.equals("stopVPN")) {
                                iJetty.stopVPN();
                                mList.remove(socket);
                                socket.close();
                            } else if (msg.equals("importVPN")) {
                                iJetty.importVPN();
                                mList.remove(socket);
                                socket.close();
                            }
                        }
                        if (in != null) {
                            in.close();
                            in = null;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /**
         * 循环遍历客户端集合，给每个客户端都发送信息。
         */
        public void writerALLClientMsg(String msg) {
            int num =mList.size();
            for (int index = 0; index < num; index ++) {
                Socket mSocket = mList.get(index);
                PrintWriter pout = null;
                try {
                    pout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())),true);
                    pout.println(msg);
                }catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        /**
         * 向某个客户端回写数据
         */
        public void writerClientMsg(String msg,Socket socket) {
            PrintWriter pout = null;
            try {
                pout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
                pout.println(msg);
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}