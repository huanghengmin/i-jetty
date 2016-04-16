package de.blinkt.openvpn.remote;

import java.io.File;

public class ConfigFileUtil {

    private static String CONFIG_FILE = "/client.ovpn";

    private static String CONFIG_PATH = "/storage/extSdCard/jetty/webapps/console";
    private static String CONFIG_PATH0 = "/storage/sdcard0/jetty/webapps/console";
    private static String CONFIG_PATH1 = "/storage/sdcard1/jetty/webapps/console";
    private static String CONFIG_PATH2 = "/sdcard/jetty/webapps/console";
    private static String CONFIG_PATH3 = "/mnt/sdcard/external_sdcard/jetty/webapps/console";
    private static String CONFIG_PATH4 = "/mnt/extsd/jetty/webapps/console";
    private static String CONFIG_PATH5 = "/jetty/webapps/console";

    public static String getConfigPath() {
        File file1 = new File(CONFIG_PATH);
        File file2 = new File(CONFIG_PATH0);
        File file3 = new File(CONFIG_PATH1);
        File file4 = new File(CONFIG_PATH2);
        File file5 = new File(CONFIG_PATH3);
        File file6 = new File(CONFIG_PATH4);
        File file7 = new File(CONFIG_PATH5);
        if (file1.exists()) {
            return CONFIG_PATH;
        } else if (file2.exists()) {
            return CONFIG_PATH0;
        } else if (file7.exists()) {
            return CONFIG_PATH5;
        } else if (file3.exists()) {
            return CONFIG_PATH1;
        } else if (file4.exists()) {
            return CONFIG_PATH2;
        } else if (file5.exists()) {
            return CONFIG_PATH3;
        } else if (file6.exists()) {
            return CONFIG_PATH4;
        } else {
            return CONFIG_PATH;
        }
    }

    public static String getConfigFile() {
        return getConfigPath() + CONFIG_FILE;
    }

}
