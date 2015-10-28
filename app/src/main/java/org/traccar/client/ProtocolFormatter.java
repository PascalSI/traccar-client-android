/*
 * Copyright 2012 - 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

public class ProtocolFormatter {
    public static String formatRequest(String address, int port, Position position) {

        Uri.Builder builder = new Uri.Builder();
        builder.scheme("http").encodedAuthority(address + ':' + port)
                .appendPath("")
                .appendQueryParameter("id", position.getDeviceId())
                .appendQueryParameter("timestamp", String.valueOf(position.getTime().getTime()))
                .appendQueryParameter("lat", String.valueOf(position.getLatitude()))
                .appendQueryParameter("lon", String.valueOf(position.getLongitude()))
                .appendQueryParameter("hacc", String.valueOf(position.getHorizontalAccuracy()))
                .appendQueryParameter("speed", String.valueOf(position.getSpeed()))
                .appendQueryParameter("bearing", String.valueOf(position.getCourse()))
                .appendQueryParameter("altitude", String.valueOf(position.getAltitude()))
                .appendQueryParameter("batt", String.valueOf(position.getBattery()));

        String url = builder.build().toString();
        Log.d("ProtocolFormatter", url);
        return url;
    }

    public static Pair<String, String> formatRequest(String address, int port, List<Position> positions) {

        if (positions.size() == 1)
            return new Pair<>(formatRequest(address, port, positions.get(0)), null);
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("http").encodedAuthority(address + ':' + port)
                .appendPath("");
        List<String> records = new LinkedList<>();
        for (Position position: positions) {
            builder.clearQuery()
                    .appendQueryParameter("id", position.getDeviceId())
                    .appendQueryParameter("timestamp", String.valueOf(position.getTime().getTime()))
                    .appendQueryParameter("lat", String.valueOf(position.getLatitude()))
                    .appendQueryParameter("lon", String.valueOf(position.getLongitude()))
                    .appendQueryParameter("hacc", String.valueOf(position.getHorizontalAccuracy()))
                    .appendQueryParameter("speed", String.valueOf(position.getSpeed()))
                    .appendQueryParameter("bearing", String.valueOf(position.getCourse()))
                    .appendQueryParameter("altitude", String.valueOf(position.getAltitude()))
                    .appendQueryParameter("batt", String.valueOf(position.getBattery()));
            String record = builder.build().getEncodedQuery();
            records.add(record);
        }

        String url = builder.clearQuery().build().toString();
        Pair<String, String> result = new Pair<>(url, TextUtils.join("\n", records));
        Log.d("ProtocolFormatter", result.toString());
        return result;
    }
}
