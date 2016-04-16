package org.mortbay.ijetty;

import java.io.DataOutputStream;
import java.io.IOException;

final class RebootOrShutdown extends Thread {
    public static final String CMD_REBOOT = "reboot";
    public static final String CMD_SHUTDOWN = "reboot -p";

    private final String strEnter = "\n";
    private final String cmd_su = "su";
    private final String cmd_exit = "exit";

    private String cmd = "";


    public void reboot(String command) {
        if (!command.equals(CMD_REBOOT) && !command.equals(CMD_SHUTDOWN))
            return;

        cmd = command;
        start();
    }

    @Override
    public void run() {
        try {
            sleep(1000);
            Process localProcess = Runtime.getRuntime().exec(cmd_su);
            DataOutputStream localDataOutputStream = new DataOutputStream(localProcess.getOutputStream());
            localDataOutputStream.writeBytes(cmd + strEnter);
            localDataOutputStream.writeBytes(cmd_exit + strEnter);
            localDataOutputStream.flush();
            localDataOutputStream.close();
            localProcess.waitFor();
            localProcess.destroy();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}