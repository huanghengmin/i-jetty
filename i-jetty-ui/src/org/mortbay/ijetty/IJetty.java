//========================================================================
//$Id: IJetty.java 474 2012-01-23 03:07:14Z janb.webtide $
//Copyright 2008 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.ijetty;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import android.content.*;
import android.os.*;
import android.widget.Toast;
import com.socks5.Socks5;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;
import de.blinkt.openvpn.remote.ParseUtil;
import org.eclipse.jetty.util.IO;
import org.mortbay.ijetty.log.AndroidLog;
import org.mortbay.ijetty.util.AndroidInfo;
import org.mortbay.ijetty.util.IJettyToast;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * IJetty
 * <p/>
 * Main Jetty activity. Can start other activities: + configure + download
 * <p/>
 * Can start/stop services: + IJettyService
 */
public class IJetty extends Activity {

    private static final String TAG = "Jetty";

    public static final String __START_ACTION = "org.mortbay.ijetty.start";
    public static final String __STOP_ACTION = "org.mortbay.ijetty.stop";

    public static final String __PORT = "org.mortbay.ijetty.port";
    public static final String __NIO = "org.mortbay.ijetty.nio";
    public static final String __SSL = "org.mortbay.ijetty.ssl";

    public static final String __CONSOLE_PWD = "org.mortbay.ijetty.console";
    public static final String __PORT_DEFAULT = "8080";
    public static final boolean __NIO_DEFAULT = true;
    public static final boolean __SSL_DEFAULT = false;

    public static final String __CONSOLE_PWD_DEFAULT = "admin";

    public static final String __WEBAPP_DIR = "webapps";
    public static final String __ETC_DIR = "etc";
    public static final String __CONTEXTS_DIR = "contexts";

    public static final String __TMP_DIR = "tmp";
    public static final String __WORK_DIR = "work";
    public static final int __SETUP_PROGRESS_DIALOG = 0;
    public static final int __SETUP_DONE = 2;
    public static final int __SETUP_RUNNING = 1;
    public static final int __SETUP_NOTDONE = 0;

    SocketThread socketThread = null;

    public static final File __JETTY_DIR;
    private Button startButton;
    private Button stopButton;
    private Button configButton;
    private TextView footer;
    private TextView info;
    private TextView console;
    private ScrollView consoleScroller;
    private StringBuilder consoleBuffer = new StringBuilder();
    private Runnable scrollTask;
    private ProgressDialog progressDialog;
    private Thread progressThread;
    private Handler handler;
    private BroadcastReceiver bcastReceiver;

    private static String status;


    public static final int START_PROFILE_EMBEDDED = 0;
    public static final int START_PROFILE_BYUUID = 1;
    public static final int ICS_OPENVPN_PERMISSION = 2;

    protected IOpenVPNAPIService mService = null;
    private String mStartUUID = null;

    /**
     *
     */
    public void startEmbeddedProfile() {
        ParseUtil p = new ParseUtil();
        VpnProfile vp = p.config(getApplicationContext());
        String config = vp.getConfigFile(getApplicationContext(), false);
        try {
            mService.addVPNProfile("client", config);
            List<APIVpnProfile> list = mService.getProfiles();
            if(list!=null&&list.size()>0) {
                mStartUUID = list.get(0).mUUID;
            }
            mService.startProfile(mStartUUID);
            // 开启服务
            mService.startVPN(config);
        }catch (RemoteException e){
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *
     */
    public void importVPN(){
        ParseUtil p = new ParseUtil();
        VpnProfile vp = p.config(getApplicationContext());
        String config = vp.getConfigFile(getApplicationContext(), false);
        try {
            mService.addVPNProfile("client", config);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public void startVPN(){
        try {
            List<APIVpnProfile> list = mService.getProfiles();
            if(list!=null&&list.size()>0) {
                mStartUUID = list.get(0).mUUID;
            }
            mService.startProfile(mStartUUID);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public void stopVPN(){
        try {
            mService.disconnect();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public String getStatus(){
        return status;
    }

    /**
     *
     */
    private IOpenVPNStatusCallback mCallback = new IOpenVPNStatusCallback.Stub() {
        @Override
        public void newStatus(String uuid, String state, String message,String level) throws RemoteException {
            status =  state + "|" + message;
        }
    };

    /**
     *
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IOpenVPNAPIService.Stub.asInterface(service);
            try {
                Intent i = mService.prepare(getApplicationContext().getPackageName());
                if (i != null) {
                    startActivityForResult(i, ICS_OPENVPN_PERMISSION);
                } else {
                    onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK, null);
                }
            } catch (RemoteException e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    /**
     *
     */
    private void bindService() {
        getApplicationContext().bindService(new Intent(IOpenVPNAPIService.class.getName()), mConnection,Context.BIND_AUTO_CREATE);
    }

    /**
     *
     */
    protected void listVPNs() {
        try {
            List<APIVpnProfile> list = mService.getProfiles();
            if (list.size() > 0) {
                mStartUUID = list.get(0).mUUID;
            }
        } catch (RemoteException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *
     */
    private void unbindService() {
        getApplicationContext().unbindService(mConnection);
    }

    /**
     *
     */
    public void prepareStartProfile(int requestCode) throws RemoteException {
        Intent requestPermission = mService.prepareVPNService();
        if (requestPermission == null) {
            onActivityResult(requestCode, Activity.RESULT_OK, null);
        } else {
            startActivityForResult(requestPermission, requestCode);
        }
    }

    /**
     *
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == START_PROFILE_EMBEDDED)
                startEmbeddedProfile();
            if (requestCode == START_PROFILE_BYUUID)
                try {
                    mService.startProfile(mStartUUID);
                } catch (RemoteException e) {
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            if (requestCode == ICS_OPENVPN_PERMISSION) {
                listVPNs();
                try {
                    mService.registerStatusCallback(mCallback);
                } catch (RemoteException e) {
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    class ConsoleScrollTask implements Runnable {
        public void run() {
            consoleScroller.fullScroll(View.FOCUS_DOWN);
        }
    }

    /**
     * ProgressThread
     * <p/>
     * Handles finishing install tasks for Jetty.
     */
    class ProgressThread extends Thread {
        private Handler _handler;

        public ProgressThread(Handler h) {
            _handler = h;
        }

        public void sendProgressUpdate(int prog) {
            Message msg = _handler.obtainMessage();
            Bundle b = new Bundle();
            b.putInt("prog", prog);
            msg.setData(b);
            _handler.sendMessage(msg);
        }

        public void run() {
            boolean updateNeeded = isUpdateNeeded();

            //create the jetty dir structure
            File jettyDir = __JETTY_DIR;
            if (!jettyDir.exists()) {
                boolean made = jettyDir.mkdirs();
                Log.i(TAG, "Made " + __JETTY_DIR + ": " + made);
            }

            sendProgressUpdate(10);


            //Do not make a work directory to preserve unpacked
            //webapps - this seems to clash with Android when
            //out-of-date webapps are deleted and then re-unpacked
            //on a jetty restart: Android remembers where the dex
            //file of the old webapp was installed, but it's now
            //been replaced by a new file of the same name. Strangely,
            //this does not seem to affect webapps unpacked to tmp?
            //Original versions of i-jetty created a work directory. So
            //we will delete it here if found to ensure webapps can be
            //updated successfully.
            File workDir = new File(jettyDir, __WORK_DIR);
            if (workDir.exists()) {
                Installer.delete(workDir);
                Log.i(TAG, "removed work dir");
            }


            //make jetty/tmp
            File tmpDir = new File(jettyDir, __TMP_DIR);
            if (!tmpDir.exists()) {
                boolean made = tmpDir.mkdirs();
                Log.i(TAG, "Made " + tmpDir + ": " + made);
            } else {
                Log.i(TAG, tmpDir + " exists");
            }

            //make jetty/webapps
            File webappsDir = new File(jettyDir, __WEBAPP_DIR);
            if (!webappsDir.exists()) {
                boolean made = webappsDir.mkdirs();
                Log.i(TAG, "Made " + webappsDir + ": " + made);
            } else {
                Log.i(TAG, webappsDir + " exists");
            }

            //make jetty/etc
            File etcDir = new File(jettyDir, __ETC_DIR);
            if (!etcDir.exists()) {
                boolean made = etcDir.mkdirs();
                Log.i(TAG, "Made " + etcDir + ": " + made);
            } else {
                Log.i(TAG, etcDir + " exists");
            }
            sendProgressUpdate(30);


            File webdefaults = new File(etcDir, "webdefault.xml");
            if (!webdefaults.exists() || updateNeeded) {
                //get the webdefaults.xml file out of resources
                try {
                    InputStream is = getResources().openRawResource(R.raw.webdefault);
                    OutputStream os = new FileOutputStream(webdefaults);
                    IO.copy(is, os);
                    Log.i(TAG, "Loaded webdefault.xml");
                } catch (Exception e) {
                    Log.e(TAG, "Error loading webdefault.xml", e);
                }
            }
            sendProgressUpdate(40);

            File realm = new File(etcDir, "realm.properties");
            if (!realm.exists() || updateNeeded) {
                try {
                    //get the realm.properties file out resources
                    InputStream is = getResources().openRawResource(R.raw.realm_properties);
                    OutputStream os = new FileOutputStream(realm);
                    IO.copy(is, os);
                    Log.i(TAG, "Loaded realm.properties");
                } catch (Exception e) {
                    Log.e(TAG, "Error loading realm.properties", e);
                }
            }
            sendProgressUpdate(50);

            File keystore = new File(etcDir, "keystore");
            if (!keystore.exists() || updateNeeded) {
                try {
                    //get the keystore out of resources
                    InputStream is = getResources().openRawResource(R.raw.keystore);
                    OutputStream os = new FileOutputStream(keystore);
                    IO.copy(is, os);
                    Log.i(TAG, "Loaded keystore");
                } catch (Exception e) {
                    Log.e(TAG, "Error loading keystore", e);
                }
            }
            sendProgressUpdate(60);

            //make jetty/contexts
            File contextsDir = new File(jettyDir, __CONTEXTS_DIR);
            if (!contextsDir.exists()) {
                boolean made = contextsDir.mkdirs();
                Log.i(TAG, "Made " + contextsDir + ": " + made);
            } else {
                Log.i(TAG, contextsDir + " exists");
            }
            sendProgressUpdate(70);

            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                if (pi != null) {
                    setStoredJettyVersion(pi.versionCode);
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to get PackageInfo for i-jetty");
            }

            //if there was a .update file indicating an update was needed, remove it now we've updated
            File update = new File(__JETTY_DIR, ".update");
            if (update.exists())
                update.delete();

            sendProgressUpdate(100);
        }
    };

    static {
        __JETTY_DIR = new File(Environment.getExternalStorageDirectory(), "jetty");
        // Ensure parsing is not validating - does not work with android
        System.setProperty("org.eclipse.jetty.xml.XmlParser.Validating", "false");

        // Bridge Jetty logging to Android logging
        System.setProperty("org.eclipse.jetty.util.log.class", "org.mortbay.ijetty.AndroidLog");
        org.eclipse.jetty.util.log.Log.setLog(new AndroidLog());
    }

    public IJetty() {
        super();

        handler = new Handler() {
            public void handleMessage(Message msg) {
                int total = msg.getData().getInt("prog");
                progressDialog.setProgress(total);
                if (total >= 100) {
                    dismissDialog(__SETUP_PROGRESS_DIALOG);
                }
            }

        };
    }

    public String formatJettyInfoLine(String format, Object... args) {
        String ms = "";
        if (format != null)
            ms = String.format(format, args);
        return ms + "<br/>";
    }

    public void consolePrint(String format, Object... args) {
        String msg = String.format(format, args);
        if (msg.length() > 0) {
            consoleBuffer.append(msg).append("<br/>");
            console.setText(Html.fromHtml(consoleBuffer.toString()));
            Log.i(TAG, msg); // Only interested in non-empty lines being output to Log
        } else {
            consoleBuffer.append(msg).append("<br/>");
            console.setText(Html.fromHtml(consoleBuffer.toString()));
        }

        if (scrollTask == null) {
            scrollTask = new ConsoleScrollTask();
        }

        consoleScroller.post(scrollTask);
    }

    protected int getStoredJettyVersion() {
        File jettyDir = __JETTY_DIR;
        if (!jettyDir.exists()) {
            return -1;
        }
        File versionFile = new File(jettyDir, "version.code");
        if (!versionFile.exists()) {
            return -1;
        }
        int val = -1;
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(versionFile));
            val = ois.readInt();
            return val;
        } catch (Exception e) {
            Log.e(TAG, "Problem reading version.code", e);
            return -1;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (Exception e) {
                    Log.d(TAG, "Error closing version.code input stream", e);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (bcastReceiver != null)
            unregisterReceiver(bcastReceiver);
        unbindService();
        super.onDestroy();
    }

    /**
     *
     */
     public void clickStartIJetty(){
        if (isUpdateNeeded()) {
            IJettyToast.showQuickToast(IJetty.this, R.string.loading);
        }else{
            //TODO get these values from editable UI elements
            Intent intent = new Intent(IJetty.this,IJettyService.class);
            intent.putExtra(__PORT,__PORT_DEFAULT);
            intent.putExtra(__NIO,__NIO_DEFAULT);
            intent.putExtra(__SSL,__SSL_DEFAULT);
            intent.putExtra(__CONSOLE_PWD,__CONSOLE_PWD_DEFAULT);
            startService(intent);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        bindService();
        super.onCreate(icicle);

        setContentView(R.layout.jetty_controller);

        startButton = (Button) findViewById(R.id.start);
        stopButton = (Button) findViewById(R.id.stop);
        configButton = (Button) findViewById(R.id.config);


//        Button startNatButton = (Button) findViewById(R.id.startNat);

        // Watch for button clicks.
       /* startNatButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Socks5 socks5 = new Socks5();
                socks5.pipeAdd("127.0.0.1","9999","192.168.4.171","80","tcp");
            }
        });*/


        final Button downloadButton = (Button) findViewById(R.id.download);

        IntentFilter filter = new IntentFilter();
        filter.addAction(__START_ACTION);
        filter.addAction(__STOP_ACTION);
        filter.addCategory("default");

        bcastReceiver =
                new BroadcastReceiver() {

                    public void onReceive(Context context, Intent intent) {
                        if (__START_ACTION.equalsIgnoreCase(intent.getAction())) {
                            startButton.setEnabled(false);
                            configButton.setEnabled(false);
                            stopButton.setEnabled(true);
                            consolePrint("<br/>Started Jetty at %s", new Date());
                            String[] connectors = intent.getExtras().getStringArray("connectors");
                            if (null != connectors) {
                                for (int i = 0; i < connectors.length; i++)
                                    consolePrint(connectors[i]);
                            }

                            printNetworkInterfaces();

                            if (AndroidInfo.isOnEmulator(IJetty.this))
                                consolePrint("Set up port forwarding to see i-jetty outside of the emulator.");
                        } else if (__STOP_ACTION.equalsIgnoreCase(intent.getAction())) {
                            startButton.setEnabled(true);
                            configButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            consolePrint("<br/> Jetty stopped at %s", new Date());
                        }
                    }

                };

        registerReceiver(bcastReceiver, filter);


        // Watch for button clicks.
        startButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (isUpdateNeeded())
                    IJettyToast.showQuickToast(IJetty.this, R.string.loading);
                else {
                    //TODO get these values from editable UI elements
                    Intent intent = new Intent(IJetty.this, IJettyService.class);
                    intent.putExtra(__PORT, __PORT_DEFAULT);
                    intent.putExtra(__NIO, __NIO_DEFAULT);
                    intent.putExtra(__SSL, __SSL_DEFAULT);
                    intent.putExtra(__CONSOLE_PWD, __CONSOLE_PWD_DEFAULT);
                    startService(intent);
                }
            }
        });

        stopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(IJetty.this, IJettyService.class));
            }
        });

        configButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                IJettyEditor.show(IJetty.this);
            }
        });

        downloadButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                IJettyDownloader.show(IJetty.this);
            }
        });

        info = (TextView) findViewById(R.id.info);
        footer = (TextView) findViewById(R.id.footer);
        console = (TextView) findViewById(R.id.console);
        consoleScroller = (ScrollView) findViewById(R.id.consoleScroller);

        StringBuilder infoBuffer = new StringBuilder();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            infoBuffer.append(formatJettyInfoLine("i-jetty version %s (%s)", pi.versionName, pi.versionCode));
        } catch (NameNotFoundException e) {
            infoBuffer.append(formatJettyInfoLine("i-jetty version unknown"));
        }
        infoBuffer.append(formatJettyInfoLine("On %s using Android version %s", AndroidInfo.getDeviceModel(), AndroidInfo.getOSVersion()));
        info.setText(Html.fromHtml(infoBuffer.toString()));

        StringBuilder footerBuffer = new StringBuilder();
        footerBuffer.append("<b>Project:</b> <a href=\"http://code.google.com/p/i-jetty\">http://code.google.com/p/i-jetty</a> <br/>");
        footerBuffer.append("<b>Server:</b> http://www.eclipse.org/jetty <br/>");
        footerBuffer.append("<b>Support:</b> http://www.intalio.com/jetty/services <br/>");
        footer.setText(Html.fromHtml(footerBuffer.toString()));


        /**
         *
         */
        if(startButton.isEnabled()){
            clickStartIJetty();
            Socks5 socks5 = new Socks5();
            final int port = 1080;
            final String data = "";
            socks5.start(port,data);
        }

        /**
         *
         */
        if(socketThread==null){
            final int port = 7505;
            socketThread =  new SocketThread(this,port);
            boolean flag = socketThread.isRun();
            if(!flag){
                socketThread.start();
            }
        }else {
            boolean flag = socketThread.isRun();
            if(!flag){
                socketThread.start();
            }
        }
    }


    /**
     *
     */
    class SocketThread extends Thread{
        private boolean isRun = false;

        private IJetty iJetty;
        private int port;

        public SocketThread(IJetty iJetty,int port) {
            this.iJetty = iJetty;
            this.port = port;
        }

        public boolean isRun(){
            return isRun;
        }

        public void run(){
            if(!isRun) {
                try {
                    new SocketServer(port, iJetty);
                    isRun = true;
                } catch (IOException e) {
                    isRun = false;
                }
            }
        }
    }


    public static void show(Context context) {
        final Intent intent = new Intent(context, IJetty.class);
        context.startActivity(intent);
    }

    @Override
    protected void onResume() {
        if (!SdCardUnavailableActivity.isExternalStorageAvailable()) {
            SdCardUnavailableActivity.show(this);
        } else {
            //work out if we need to do the installation finish step
            //or not. We do it iff:
            // - there is no previous jetty version on disk
            // - the previous version does not match the current version
            // - we're not already doing the update
            if (isUpdateNeeded()) {
                setupJetty();
            }
        }

        if (IJettyService.isRunning()) {
            startButton.setEnabled(false);
            configButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            startButton.setEnabled(true);
            configButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
        super.onResume();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case __SETUP_PROGRESS_DIALOG: {
                progressDialog = new ProgressDialog(IJetty.this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMessage("Finishing initial install ...");

                return progressDialog;
            }
            default:
                return null;
        }
    }

    private void printNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nis)) {
                Enumeration<InetAddress> iis = ni.getInetAddresses();
                for (InetAddress ia : Collections.list(iis)) {
                    consoleBuffer.append(formatJettyInfoLine("Network interface: %s: %s", ni.getDisplayName(), ia.getHostAddress()));
                }
            }
        } catch (SocketException e) {
            Log.w(TAG, e);
        }
    }

    protected void setStoredJettyVersion(int version) {
        File jettyDir = __JETTY_DIR;
        if (!jettyDir.exists()) {
            return;
        }
        File versionFile = new File(jettyDir, "version.code");
        ObjectOutputStream oos = null;
        try {
            FileOutputStream fos = new FileOutputStream(versionFile);
            oos = new ObjectOutputStream(fos);
            oos.writeInt(version);
            oos.flush();
        } catch (Exception e) {
            Log.e(TAG, "Problem writing jetty version", e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {
                    Log.d(TAG, "Error closing version.code output stream", e);
                }
            }
        }
    }

    /**
     * We need to an update iff we don't know the current
     * jetty version or it is different to the last version
     * that was installed.
     *
     * @return
     */
    public boolean isUpdateNeeded() {
        //if no previous version file, assume update is required
        int storedVersion = getStoredJettyVersion();
        if (storedVersion <= 0)
            return true;

        try {
            //if different previous version, update is required
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi == null)
                return true;
            if (pi.versionCode != storedVersion)
                return true;

            //if /sdcard/jetty/.update file exists, then update is required
            File alwaysUpdate = new File(__JETTY_DIR, ".update");
            if (alwaysUpdate.exists()) {
                Log.i(TAG, "Always Update tag found " + alwaysUpdate);
                return true;
            }
        } catch (Exception e) {
            //if any of these tests go wrong, best to assume update is true?
            return true;
        }

        return false;
    }

    public void setupJetty() {
        showDialog(__SETUP_PROGRESS_DIALOG);
        progressThread = new ProgressThread(handler);
        progressThread.start();
    };

}
