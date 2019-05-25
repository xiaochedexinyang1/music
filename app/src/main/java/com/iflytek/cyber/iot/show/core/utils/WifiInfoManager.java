/*
 * Copyright (C) 2018 iFLYTEK CO.,LTD.
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

package com.iflytek.cyber.iot.show.core.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WifiInfoManager {

    private static final WifiInfoManager sManager = new WifiInfoManager();

    private List<NetworkStateListener> listeners = new ArrayList<>();
    private List<WifiRssiListener> rssiListeners = new ArrayList<>();

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public static WifiInfoManager getManager() {
        return sManager;
    }

    private WifiInfoManager() {
        if (Build.VERSION.SDK_INT >= 21)
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(final Network network) {
                    super.onAvailable(network);
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (NetworkStateListener listener : listeners) {
                                listener.onAvailable(network);
                            }
                        }
                    });
                }

                @Override
                public void onLost(final Network network) {
                    super.onLost(network);
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (NetworkStateListener listener : listeners) {
                                listener.onLost(network);
                            }
                        }
                    });
                }
            };
    }

    @Nullable
    public WifiInfo getWifiInfo(Context context) {
        final WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (manager != null) {
            return manager.getConnectionInfo();
        } else {
            return null;
        }
    }

    public int getWifiSignalLevel(Context context) {
        final WifiInfo wifiInfo = getWifiInfo(context);
        if (wifiInfo != null) {
            return WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
        } else {
            return 0;
        }
    }

    public boolean isNetworkAvailable(Context context) {
        final ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            final NetworkInfo networkInfo = manager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isAvailable();
        } else {
            return false;
        }
    }

    public void registerNetworkCallback(Context context, @NonNull NetworkStateListener networkStateListener) {
        listeners.add(networkStateListener);
        final ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= 21) {
            final NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            manager.registerNetworkCallback(request, networkCallback);
        }
    }

    public void unregisterNetworkCallback(Context context) {
        final ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= 21) {
            manager.unregisterNetworkCallback(networkCallback);
        }
    }

    public void registerWifiRssiCallback(Context context, WifiRssiListener listener) {
        rssiListeners.add(listener);
        context.registerReceiver(receiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
    }

    public void unregisterWifiRssiCallback(Context context) {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
        receiver = null;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (WifiRssiListener listener : rssiListeners) {
                listener.onChange();
            }
        }
    };

    private ConnectivityManager.NetworkCallback networkCallback;

    public interface NetworkStateListener {
        void onAvailable(Network network);

        void onLost(Network network);
    }

    public interface WifiRssiListener {
        void onChange();
    }

}
