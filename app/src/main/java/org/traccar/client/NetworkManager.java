/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkManager extends BroadcastReceiver {
    public enum NetworkStatus {NotReachable, ReachableViaWiFi, ReachableViaWWAN};

    private static final String TAG = NetworkManager.class.getSimpleName();

    private Context context;
    private NetworkHandler handler;
    private ConnectivityManager connectivityManager;

    public NetworkManager(Context context, NetworkHandler handler) {
        this.context = context;
        this.handler = handler;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public interface NetworkHandler {
        void onNetworkUpdate(NetworkStatus netStatus);
    }

    public NetworkStatus status() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork == null) {
            Log.i(TAG, "active network null");
            return NetworkStatus.NotReachable;
        }
        Log.i(TAG, "active network type: " + activeNetwork.getType() +
                ", connecting: " + (activeNetwork.isConnectedOrConnecting() && !activeNetwork.isConnected()) +
                ", connected: " + activeNetwork.isConnected());
        if (!activeNetwork.isConnected())
            return NetworkStatus.NotReachable;
        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
            return NetworkStatus.ReachableViaWiFi;
        return NetworkStatus.ReachableViaWWAN;
    }

    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, filter);
    }

    public void stop() {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION) && handler != null) {
            NetworkStatus netStatus = status();
            Log.i(TAG, "network " + netStatus);
            handler.onNetworkUpdate(netStatus);
        }
    }

}
