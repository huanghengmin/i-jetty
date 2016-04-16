package com.socks5;

public class Socks5  {

    /*start(服务端口, "可访问IP-one:端口,可访问IP-two:端口");*/

    public native boolean  start(int port,String str);

    public native boolean  stop();

    public native boolean  pipeAdd(String ip,String port,String sendIp,String sendPort,String protocol);

    public native boolean  pipeStop(String ip,String port,String sendIp,String sendPort,String protocol);

    static {
        System.loadLibrary("socks5");
    }
}
