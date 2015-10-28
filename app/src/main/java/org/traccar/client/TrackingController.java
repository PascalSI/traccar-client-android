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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Date;
import java.util.List;

public class TrackingController implements PositionProvider.PositionListener, NetworkManager.NetworkHandler {

    private static final String TAG = TrackingController.class.getSimpleName();
    private static final int RETRY_DELAY = 30 * 1000;
    private static final int WAKE_LOCK_TIMEOUT = 60 * 1000;

    private boolean isOnline;
    private boolean isWaiting;

    private Context context;
    private Handler handler;

    private String address;
    private int port;
    private int batchReportNum;
    private int reportInterval;
    private Date lastSuccessReport;

    private PositionProvider positionProvider;
    private DatabaseHelper databaseHelper;
    private NetworkManager networkManager;

    private PowerManager.WakeLock wakeLock;

    private void lock() {
        wakeLock.acquire(WAKE_LOCK_TIMEOUT);
    }

    private void unlock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public TrackingController(Context context) {
        this.context = context;
        handler = new Handler();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getString(MainActivity.KEY_PROVIDER, null).equals("mixed")) {
            positionProvider = new MixedPositionProvider(context, this);
        } else {
            positionProvider = new SimplePositionProvider(context, this);
        }
        databaseHelper = new DatabaseHelper(context);
        networkManager = new NetworkManager(context, this);
        isOnline = networkManager.isOnline();

        address = preferences.getString(MainActivity.KEY_ADDRESS, null);
        port = Integer.parseInt(preferences.getString(MainActivity.KEY_PORT, null));
        batchReportNum = Integer.parseInt(preferences.getString(MainActivity.KEY_BATCH_REPORT_NUM, null));
        if (batchReportNum < 1)
            batchReportNum = 1;
        reportInterval = Integer.parseInt(preferences.getString(MainActivity.KEY_REPORT_INTERVAL, null));

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
    }

    public void start() {
        if (isOnline) {
            read();
        }
        positionProvider.startUpdates();
        networkManager.start();
    }

    public void stop() {
        networkManager.stop();
        positionProvider.stopUpdates();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onPositionUpdate(Position position) {
        StatusActivity.addMessage(context.getString(R.string.status_location_update));
        if (position != null) {
            write(position);
        }
    }

    @Override
    public void onNetworkUpdate(boolean isOnline) {
        StatusActivity.addMessage(context.getString(R.string.status_connectivity_change));
        if (!this.isOnline && isOnline) {
            read();
        }
        this.isOnline = isOnline;
    }

    //
    // State transition examples:
    //
    // write -> read -> send -> delete -> read
    //
    // read -> send -> retry -> read -> send
    //

    private void log(String action) {
        Log.d(TAG, action);
    }
    private void log(String action, Position position) {
        if (position != null) {
            action += " (" +
                    "id:" + position.getId() +
                    " time:" + position.getTime().getTime() +
                    " lat:" + position.getLatitude() +
                    " lon:" + position.getLongitude() +
                    " hacc:" + position.getHorizontalAccuracy() + ")";
        }
        Log.d(TAG, action);
    }
    private void log(String action, List<Position> positions) {
        if (positions != null) {
            for (Position position: positions)
                action += " (" +
                        "id:" + position.getId() +
                        " time:" + position.getTime().getTime() +
                        " lat:" + position.getLatitude() +
                        " lon:" + position.getLongitude() +
                        " hacc:" + position.getHorizontalAccuracy() + ")";
        }
        Log.d(TAG, action);
    }

    private void write(Position position) {
        log("write", position);
        lock();
        databaseHelper.insertPositionAsync(position, new DatabaseHelper.DatabaseHandler<Void>() {
            @Override
            public void onComplete(boolean success, Void result) {
                if (success) {
                    if (isOnline && isWaiting) {
                        read();
                        isWaiting = false;
                    }
                }
                unlock();
            }
        });
    }

    private void doRead() {
        log("doRead");
        lock();
        databaseHelper.selectPositionsAsync(batchReportNum, new DatabaseHelper.DatabaseHandler<List<Position>>() {
            @Override
            public void onComplete(boolean success, List<Position> result) {
                if (success) {
                    if (result != null) {
                        send(result);
                    } else {
                        isWaiting = true;
                    }
                } else {
                    retry();
                }
                unlock();
            }
        });
    }

    private void read() {
        log("read");
        if (lastSuccessReport != null) {
            long intervalLeft = new Date().getTime() - lastSuccessReport.getTime() - reportInterval * 1000;
            if (intervalLeft > 0) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isOnline) {
                            doRead();
                        }
                    }
                }, intervalLeft);
                return;
            }
        }
        doRead();
    }

    private void delete(List<Position> positions) {
        log("delete", positions);
        lock();
        databaseHelper.deletePositionsAsync(positions, new DatabaseHelper.DatabaseHandler<Void>() {
            @Override
            public void onComplete(boolean success, Void result) {
                if (success) {
                    read();
                } else {
                    retry();
                }
                unlock();
            }
        });
    }

    private void send(final List<Position> positions) {
        log("send", positions);
        lock();
        final Date requestTime = new Date();
        Pair<String, String> request = ProtocolFormatter.formatRequest(address, port, positions);
        RequestManager.sendRequestAsync(request, new RequestManager.RequestHandler() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    delete(positions);
                    lastSuccessReport = requestTime;
                } else {
                    StatusActivity.addMessage(context.getString(R.string.status_send_fail));
                    retry();
                }
                unlock();
            }
        });
    }

    private void retry() {
        log("retry");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isOnline) {
                    read();
                }
            }
        }, RETRY_DELAY);
    }

}
