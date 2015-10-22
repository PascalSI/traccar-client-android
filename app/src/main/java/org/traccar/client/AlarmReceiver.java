package org.traccar.client;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * Created by ulion on 15/10/21.
 */
public class AlarmReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // do reschedule, we did similar as the Auto start receiver?
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getBoolean(MainActivity.KEY_STATUS, false)) {
            TrackingScheduler.getInstance(context).startService(this);
        }
        else {
            // should we stop the service here??? would we sure the service is already stopped here?
        }
    }

}
