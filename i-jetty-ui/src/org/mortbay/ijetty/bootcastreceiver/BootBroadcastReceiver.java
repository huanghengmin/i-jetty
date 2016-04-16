package org.mortbay.ijetty.bootcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.mortbay.ijetty.IJetty;

/**
 * Created by Administrator on 14-7-29.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String action_boot = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(action_boot)){
            Intent ootStartIntent = new Intent(context,IJetty.class);
            ootStartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(ootStartIntent);
        }
    }

}
