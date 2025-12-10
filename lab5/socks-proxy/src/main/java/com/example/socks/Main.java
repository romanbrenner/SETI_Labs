package com.example.socks;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java -jar socks-proxy.jar <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        SocksProxy proxy = new SocksProxy(port);
        proxy.start();
    }
}
