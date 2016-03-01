package com.example.android.sunshine.wear.service;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;


/**
 * Created by setico on 27/02/2016.
 */

public class SunshineSyncService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    static WeatherDataCallback weatherDataCallback;

    public static void onCallback(WeatherDataCallback callback) {
        weatherDataCallback = callback;
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                return;
            }
        }

        for (DataEvent dataEvent : dataEvents){

            if (dataEvent.getType() == DataEvent.TYPE_CHANGED){
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                if (dataEvent.getDataItem().getUri().getPath().equals("/sunshine")){
                    Bitmap icon = null;
                    Asset asset = dataMap.getAsset("icon");
                    if (asset != null) {
                        InputStream inputStream = Wearable.DataApi.getFdForAsset(
                                mGoogleApiClient, asset).await().getInputStream();
                        if (inputStream != null) {
                            icon = BitmapFactory.decodeStream(inputStream);;
                        }
                    }
                    weatherDataCallback.getMinTemp(dataMap.getDouble("min_temp", 23));
                    weatherDataCallback.getMaxTemp(dataMap.getDouble("max_temp", 23));
                    weatherDataCallback.getIcon(icon);
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public interface WeatherDataCallback {
        void getMinTemp(double min);
        void getMaxTemp(double max);
        void getIcon(Bitmap icon);
    }
}
