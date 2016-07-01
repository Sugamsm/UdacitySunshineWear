/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {
        private static final long TIMEOUT_MS = 3000;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mLinePaint;
        Paint mDatePaint;
        Paint mStatsPaint;
        Paint mHighPaint;
        Paint mLowPaint;
        Paint mDescPaint;
        boolean mAmbient;
        Paint mAP;
        SimpleDateFormat dt;
        String high = "NA", low = "NA", desc = "NA", time;
        Bitmap bmp;
        boolean is24Hour = DateFormat.is24HourFormat(MyWatchFace.this);

        Calendar mCalendar;
        Date mDate;

        float ip, xip, round_x, round_y, desc_y, desc_x;


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
                initFormats();
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        int mTapCount;

        float mXOffset;
        float mYOffset;
        float art_size;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.trans_white));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.trans_white));

            mStatsPaint = new Paint();

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.trans_white));

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mAP = new Paint();
            mAP = createTextPaint(resources.getColor(R.color.trans_white));

            mDescPaint = new Paint();
            mDescPaint = createTextPaint(resources.getColor(R.color.digital_text));

            dt = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
            initFormats();
        }


        private void initFormats() {
            dt.setCalendar(mCalendar);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round_new : R.dimen.digital_x_offset_new);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_round : R.dimen.digital_time_text);

            mTextPaint.setTextSize(textSize);
            float dateText = resources.getDimension(isRound ? R.dimen.digital_date_text_round : R.dimen.digital_date_text);
            mDatePaint.setTextSize(dateText);

            mAP.setTextSize(dateText);

            art_size = resources.getDimension(isRound ? R.dimen.art_size_round : R.dimen.art_size);

            float degreeText = resources.getDimension(isRound ? R.dimen.degree_size_round : R.dimen.degree_size);
            mHighPaint.setTextSize(degreeText);
            mLowPaint.setTextSize(degreeText);
            mDescPaint.setTextSize(degreeText - 5);
            bmp = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher), (int) art_size, (int) art_size, true);
            ip = resources.getDimension(isRound ? R.dimen.inner_padding_round : R.dimen.inner_padding);
            xip = resources.getDimension(isRound ? R.dimen.innex_x_padding_round : R.dimen.innex_x_padding);

            if (isRound) {
                round_x = resources.getDimension(R.dimen.round_x);
                round_y = resources.getDimension(R.dimen.round_y);
                desc_y = round_y;

                desc_x = round_x + 5;
            } else {
                round_x = xip;
                round_y = ip;
                desc_y = ip;
                desc_x = xip;
            }

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mLinePaint.setAntiAlias(!inAmbientMode);
                    mDescPaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);
                    mStatsPaint.setFilterBitmap(!inAmbientMode);
                    mStatsPaint.setAntiAlias(!inAmbientMode);
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.primary));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.

            long now = System.currentTimeMillis();
            mDate.setTime(now);

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.

            int mHour;
            float x = mXOffset, y = mYOffset;

            if (is24Hour) {
                mHour = mCalendar.get(Calendar.HOUR_OF_DAY);
            } else {
                mHour = mCalendar.get(Calendar.HOUR);
            }

            if (mHour == 0) {
                mHour = 12;
            }

            String text = String.format(Locale.getDefault(), "%02d:%02d", mHour, mCalendar.get(Calendar.MINUTE));

            canvas.drawText(text, bounds.width() / 2 - (mTextPaint.measureText(text) / 2) - 10, mYOffset, mTextPaint);

            canvas.drawText(" " + getAP(mCalendar.get(Calendar.AM_PM)), x + mTextPaint.measureText(text) + (float) 1.5 * round_x, y, mAP);

            y += 1.5 * ip;
            canvas.drawText(dt.format(mDate), bounds.width() / 2 - (mDatePaint.measureText(dt.format(mDate)) / 2), y + 15, mDatePaint);

            y += 2 * ip;
            canvas.drawLine(x + 70, y, bounds.width() - (x + 70), y, mLinePaint);

            y += round_y;
            if (!isInAmbientMode()) {
                canvas.drawBitmap(bmp, x, y, mStatsPaint);
            }


            y += 3 * round_y;
            x += 3.5 * round_x;
            canvas.drawText(high, x, y, mHighPaint);

            x += 2 * round_x;
            canvas.drawText(low, x, y, mLowPaint);

            y += 2.5 * desc_y;
            canvas.drawText(desc, bounds.width() / 2 - (mDescPaint.measureText(desc) / 2), y, mDescPaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }


        private String getAP(int ap) {
            if (ap == 0) {
                return "AM";
            } else {
                return "PM";
            }
        }


        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            new Retrieve().execute();
        }


        public class Retrieve extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... params) {

                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

                Wearable.MessageApi.sendMessage(mGoogleApiClient, nodes.getNodes().get(0).getId(), "/sync-request", null)
                        .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    Log.i("Message Result", "Successfully Sent Message Request...");
                                } else {
                                    Log.i("Message Result", "Couldn't send request..");
                                }
                            }
                        });

                return null;
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.i("OnDataChanged", "Received Changed Data...");
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED &&
                        dataEvent.getDataItem().getUri().getPath().equals("/wear-sync")) {

                    DataMapItem mapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                    new Update().execute(mapItem.getDataMap());
                }


            }
        }


        public class Update extends AsyncTask<DataMap, Void, Void> {

            @Override
            protected Void doInBackground(DataMap... params) {
                UpdateUI(params[0]);
                return null;
            }
        }

        private void UpdateUI(final DataMap map) {
            high = map.getString("high");
            low = map.getString("low");
            desc = map.getString("desc");
            time = map.getString("time");
            Asset asset = map.getAsset("art");
            if (asset != null) {
                bmp = Bitmap.createScaledBitmap(loadBitmapFromAsset(asset), (int) art_size, (int) art_size, true);
            }
            invalidate();
        }

        public Bitmap loadBitmapFromAsset(final Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result = mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready

            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w("Load Bitmap", "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
    }
}
