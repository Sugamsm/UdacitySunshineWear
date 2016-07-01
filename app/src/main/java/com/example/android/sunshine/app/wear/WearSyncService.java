package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

public class WearSyncService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WEAR_SYNC";
    private GoogleApiClient mGoogleApiClient;

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;

    public WearSyncService() {
        super(TAG);
    }


    private void create() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        create();
        mGoogleApiClient.connect();


        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        String description = data.getString(INDEX_SHORT_DESC);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();


        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/wear-sync");
        int art = 0;
        if (weatherId >= 200 && weatherId <= 232) {
            art = R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            art = R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            art = R.drawable.ic_rain;
        } else if (weatherId == 511) {
            art = R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            art = R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            art = R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            art = R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            art = R.drawable.ic_storm;
        } else if (weatherId == 800) {
            art = R.drawable.ic_clear;
        } else if (weatherId == 801) {
            art = R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            art = R.drawable.ic_cloudy;
        }


        putDataMapRequest.getDataMap().putString("desc", description);
        putDataMapRequest.getDataMap().putString("time", "" + System.currentTimeMillis());
        putDataMapRequest.getDataMap().putString("high", formattedMaxTemperature);
        putDataMapRequest.getDataMap().putString("low", formattedMinTemperature);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), art);
        Asset asset = createFromBitmap(bitmap);
        putDataMapRequest.getDataMap().putAsset("art", asset);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                        } else {
                            Log.i("Result", "Sent Data");
                        }
                    }
                });

    }

    private static Asset createFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
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
}
