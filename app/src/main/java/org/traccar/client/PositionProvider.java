/*
 * Copyright 2013 - 2015 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public abstract class PositionProvider {

    protected static final String TAG = PositionProvider.class.getSimpleName();

    public interface PositionListener {
        void onPositionUpdate(Position position);
    }

    private final PositionListener listener;

    private final Context context;
    private final SharedPreferences preferences;
    protected final LocationManager locationManager;

    private String deviceId;
    protected String type;
    protected final long period;
    protected final long minAccuracy;
    protected final long distanceThreshold;
    protected final double speedDeltaThreshold;
    protected final long courseDeltaThreshold;

    private Location lastLocation;

    public PositionProvider(Context context, PositionListener listener) {
        this.context = context;
        this.listener = listener;

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        deviceId = preferences.getString(MainActivity.KEY_DEVICE, null);
        period = Integer.parseInt(preferences.getString(MainActivity.KEY_INTERVAL, "0")) * 1000;
        minAccuracy = Integer.parseInt(preferences.getString(MainActivity.KEY_MIN_ACCURACY, "0"));
        distanceThreshold = Integer.parseInt(preferences.getString(MainActivity.KEY_DISTANCE_THRESHOLD, "0"));
        speedDeltaThreshold = Integer.parseInt(preferences.getString(MainActivity.KEY_SPEED_DELTA_THRESHOLD, "0")) / 3.6;
        courseDeltaThreshold = Integer.parseInt(preferences.getString(MainActivity.KEY_COURSE_DELTA_THRESHOLD, "0"));

        type = preferences.getString(MainActivity.KEY_PROVIDER, null);
    }

    public abstract void startUpdates();

    public abstract void stopUpdates();

    protected void updateLocation(Location location) {
        if (location == null) {
            Log.i(TAG, "location nil");
            return;
        }
        if (lastLocation != null && location.getTime() == lastLocation.getTime()) {
            Log.i(TAG, "location old");
            return;
        }
        if (!location.hasAccuracy() || (minAccuracy > 0 && location.getAccuracy() > minAccuracy)) {
            Log.i(TAG, "location less accuracy");
            return;
        }
        if (lastLocation == null ||
                location.getAccuracy() < lastLocation.getAccuracy() ||
                location.hasSpeed() && (!lastLocation.hasSpeed() || speedDeltaThreshold > 0 && Math.abs(location.getSpeed() - lastLocation.getSpeed()) >= speedDeltaThreshold) ||
                location.hasBearing() && (!lastLocation.hasBearing() || courseDeltaThreshold > 0 && Math.abs(location.getBearing() - lastLocation.getBearing()) >= courseDeltaThreshold) ||
                location.getTime() - lastLocation.getTime() >= period ||
                distanceThreshold > 0 && location.distanceTo(lastLocation) >= distanceThreshold
                )
        Log.i(TAG, "location new: " + location.toString());
        lastLocation = location;
        listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel()));
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private double getBatteryLevel() {
        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR) {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        } else {
            return 0;
        }
    }

}
