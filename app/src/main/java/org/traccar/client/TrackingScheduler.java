package org.traccar.client;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import android.app.AlarmManager;
import android.app.PendingIntent;

import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;


/**
 * Created by ulion on 15/10/20.
 */
public class TrackingScheduler {
    private static TrackingScheduler ourInstance = null;

    public static synchronized TrackingScheduler getInstance(Context context) {
        if (ourInstance == null)
            ourInstance = new TrackingScheduler(context);
        return ourInstance;
    }

    private final Context context;
    private final SharedPreferences preferences;

    private TrackingScheduler() {
        context = null;
        preferences = null;
    }
    private TrackingScheduler(Context context) {
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void startService() {
        startService(null);
    }
    public void startService(WakefulBroadcastReceiver wakefulReceiver) {
        if (preferences.getBoolean(MainActivity.KEY_SCHEDULE, false)) {
            // we should follow the schedule rules
            Pair<Boolean, Date> statusAndTurnTime = checkStatusAndTurnTime();
            if (statusAndTurnTime.first) {
                // currently should run the service and schedule to stop service
                doStartService(wakefulReceiver);
            } else {
                // currently should stop the service and schedule to start service
                stopService();
            }
            reschedule(statusAndTurnTime.second);
            StatusActivity.addMessage("Scheduled " + (statusAndTurnTime.first?"stop":"start") + " time: " + statusAndTurnTime.second);
        } else {
            // not by schedule, so directly start the service
            doStartService(wakefulReceiver);
        }
    }

    private void reschedule(Date targetTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent("org.traccar.client.reschedule");
        i.setClass(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

        alarmManager.set(AlarmManager.RTC_WAKEUP, targetTime.getTime(), pi);
    }

    private void doStartService(WakefulBroadcastReceiver wakefulReceiver) {
        if (wakefulReceiver != null)
            wakefulReceiver.startWakefulService(context, new Intent(context, TrackingService.class));
        else
            context.startService(new Intent(context, TrackingService.class));
    }

    public void stopService() {
        // XXX: we do not remove the alarm, when it comes, it will do nothing. or it will be changed
        //      if we set a new alarm.
        context.stopService(new Intent(context, TrackingService.class));
    }

    public Pair<Boolean, Date> checkStatusAndTurnTime() {
        Calendar calendar = new GregorianCalendar();
        int dayOfWeek  = calendar.get(Calendar.DAY_OF_WEEK); // 1 = sunday, 2 = monday, ..., 7 = saturday
        int minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int goBeginTime = 7*60;
        int goEndTime = 9*60;
        int backBeginTime = 16*60;
        int backEndTime = 18*60;
        boolean nowTrack = false;
        int dayOffset = 0;
        int turnTimeOfDay = 0;
        if (dayOfWeek >= 2 && dayOfWeek <= 6) {
            // work days
            int backTimeDelta = dayOfWeek == 3 ? -60 : 0;
            if (minuteOfDay < goBeginTime) {
                turnTimeOfDay = goBeginTime;
            }
            else if (minuteOfDay >= goBeginTime && minuteOfDay < goEndTime) {
                nowTrack = true;
                turnTimeOfDay = goEndTime;
            }
            else if (minuteOfDay >= goEndTime && minuteOfDay < backBeginTime+backTimeDelta) {
                turnTimeOfDay = backBeginTime+backTimeDelta;
            }
            else if (minuteOfDay >= backBeginTime+backTimeDelta && minuteOfDay < backEndTime+backTimeDelta) {
                nowTrack = true;
                turnTimeOfDay = backEndTime + backTimeDelta;
            }
            else if (minuteOfDay >= backEndTime+backTimeDelta) {
                turnTimeOfDay = goBeginTime;
                dayOffset = 1;
                if (dayOfWeek + dayOffset > 6)
                    dayOffset = 9 - dayOfWeek;
            }
        }
        else {
            // day of the week is 1 or 7, the next work day is 2, the day offset is 1 or 2
            dayOffset = dayOfWeek == 1 ? 1 : 2;
            turnTimeOfDay = goBeginTime;
        }
        Log.d("TrackingScheduler", "now: " + calendar.getTime() + ", track: " + nowTrack);
        calendar.set(Calendar.HOUR_OF_DAY, turnTimeOfDay/60);
        calendar.set(Calendar.MINUTE, turnTimeOfDay%60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_WEEK, dayOffset);
        Log.d("TrackingScheduler", "turn time: " + calendar.getTime());
        return new Pair<>(nowTrack, calendar.getTime());
    }
}
