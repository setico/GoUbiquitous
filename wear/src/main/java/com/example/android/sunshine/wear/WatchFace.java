package com.example.android.sunshine.wear;


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

        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.content.res.Resources;
        import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.graphics.Canvas;
        import android.graphics.Paint;
        import android.graphics.Rect;
        import android.graphics.Typeface;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Message;
        import android.support.wearable.watchface.CanvasWatchFaceService;
        import android.support.wearable.watchface.WatchFaceStyle;
        import android.text.format.DateFormat;
        import android.text.format.Time;
        import android.util.Log;
        import android.view.SurfaceHolder;
        import android.view.WindowInsets;

        import com.example.android.sunshine.wear.service.SunshineSyncService;
        import com.google.android.gms.wearable.DataApi;
        import com.google.android.gms.wearable.DataEvent;
        import com.google.android.gms.wearable.DataEventBuffer;
        import com.google.android.gms.wearable.DataMap;
        import com.google.android.gms.wearable.DataMapItem;

        import java.text.SimpleDateFormat;
        import java.util.Calendar;
        import java.util.Date;
        import java.util.Locale;
        import java.util.TimeZone;
        import java.util.concurrent.TimeUnit;


/**
 * Created by setico on 27/02/2016.
 */


public class WatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String TAG = WatchFace.class.getSimpleName();


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener{
        static final int MSG_UPDATE_TIME = 0;

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = UPDATE_RATE_MS
                                    - (timeMs % UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        /**
         * Handles time zone and locale changes.
         */

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;
        private Paint mBackgroundPaint;
        private Paint mIconPaint;
        private Paint mMaxTempPaint;
        private Paint mMinTempPaint;
        private Paint mSeparatorPaint;
        private Paint mTimePaint;
        private Paint mDatePaint;
        private Paint mDayPaint;
        private Paint mLinePaint;
        private Calendar mCalendar;
        private Date mDate;
        private static final String SEPARATOR_STRING = ", ";
        private float canvasHeight;
        private float canvasWidth;
        private float textSizeTime;
        private float textSizeDate;
        private float textSizeTemp;
        private double minTemp = 20;
        private double maxTemp = 34;
        private float mLineHeight;
        private float mSeparatorWidth;
        private Bitmap mIcon;
        private SimpleDateFormat mDayOfWeekFormat;
        private SimpleDateFormat mDateFormat;

        private boolean mAmbient;

        private Time mTime;

        private float mYOffset;

        private boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = WatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTimePaint = createTextPaint(resources.getColor(R.color.text_white));
            mMaxTempPaint = createTextPaint(resources.getColor(R.color.text_white));
            mMinTempPaint = createTextPaint(resources.getColor(R.color.text_activated));
            mDatePaint = createTextPaint(resources.getColor(R.color.text_activated));
            mDayPaint = createTextPaint(resources.getColor(R.color.text_activated));
            mSeparatorPaint = createTextPaint(resources.getColor(R.color.text_activated));
            mLinePaint = createTextPaint(resources.getColor(R.color.text_activated));
            mIconPaint = new Paint();

            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clear);

            mTime = new Time();
            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mDayOfWeekFormat = new SimpleDateFormat("E");
            mDateFormat = new SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH);

            //data changed
            SunshineSyncService.onCallback(new SunshineSyncService.WeatherDataCallback() {
                @Override
                public void getMinTemp(double min) {
                    minTemp = min;
                }

                @Override
                public void getMaxTemp(double max) {
                    maxTemp = max;
                }

                @Override
                public void getIcon(Bitmap icon) {
                    mIcon = icon;
                    invalidate();
                }

            });
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
                // Update time zone
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFace.this.getResources();

            textSizeTime = resources.getDimension(R.dimen.digital_text_size);

            textSizeDate = resources.getDimension(R.dimen.digital_date_text_size);

            textSizeTemp = resources.getDimension(R.dimen.temp_text_size);

            mTimePaint.setTextSize(textSizeTime);
            mDatePaint.setTextSize(textSizeDate);
            mDayPaint.setTextSize(textSizeDate);
            mSeparatorPaint.setTextSize(textSizeDate);
            mMinTempPaint.setTextSize(textSizeTemp);
            mMaxTempPaint.setTextSize(textSizeTemp);
            mSeparatorWidth = mSeparatorPaint.measureText(SEPARATOR_STRING);
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
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mDayPaint.setAntiAlias(!inAmbientMode);
                    mMaxTempPaint.setAntiAlias(!inAmbientMode);
                    mMinTempPaint.setAntiAlias(!inAmbientMode);
                    mIconPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvasHeight = canvas.getHeight();
            canvasWidth = canvas.getWidth();
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text =  String.format("%d:%02d", mTime.hour, mTime.minute);

            canvas.drawText(text,
                    ( canvasWidth - mTimePaint.measureText(text) )/2,
                    mYOffset,
                    mTimePaint);


            if (getPeekCardPosition().isEmpty()) {

                // Day
                String dayText = mDayOfWeekFormat.format(mDate).toUpperCase();
                String dateText = mDateFormat.format(mDate).toUpperCase();
                float dayStartingX = ( canvasWidth - mDayPaint.measureText(dayText) - mSeparatorPaint.measureText(SEPARATOR_STRING)
                        - mDatePaint.measureText(dateText))/2;

                canvas.drawText(
                        dayText ,
                        dayStartingX,
                        mYOffset + mLineHeight,
                        mDayPaint);

                //separator
                canvas.drawText(
                        SEPARATOR_STRING,
                        dayStartingX + mDayPaint.measureText(dayText),
                        mYOffset + mLineHeight,
                        mSeparatorPaint);

                // Date
                canvas.drawText(
                        dateText,
                        dayStartingX + mDayPaint.measureText(dayText)+mSeparatorPaint.measureText(SEPARATOR_STRING) ,
                        mYOffset + mLineHeight,
                        mDatePaint);

                //line
                canvas.drawLine(
                        (canvasWidth - 50) / 2,
                        mYOffset + mLineHeight + textSizeDate,
                        (canvasWidth + 50) / 2,
                        mYOffset + mLineHeight + textSizeDate,
                        mLinePaint
                );
                //icon
                Bitmap bitmap =  Bitmap.createScaledBitmap(mIcon,
                        Math.round(getResources().getDimension(R.dimen.icon_size)),
                        Math.round(getResources().getDimension(R.dimen.icon_size)),
                        false);
                canvas.drawBitmap(
                        bitmap,
                        dayStartingX,
                        mYOffset + 2 * mLineHeight,
                        mIconPaint);

                //max Temp
                String maxTempStr =  String.format(getResources().getString(R.string.format_temperature), maxTemp);
                canvas.drawText(
                        maxTempStr,
                        dayStartingX + Math.round(getResources().getDimension(R.dimen.icon_size)
                                + getResources().getDimension(R.dimen.temp_icon_horiz_spacing))
                        ,
                        mYOffset + 2 * mLineHeight + (getResources().getDimension(R.dimen.icon_size) * 3/4),
                        mMaxTempPaint);


                //min Temp
                canvas.drawText(
                        String.format(getResources().getString(R.string.format_temperature), minTemp),
                        dayStartingX + Math.round(getResources().getDimension(R.dimen.icon_size)
                                +  getResources().getDimension(R.dimen.temp_icon_horiz_spacing)
                                +  getResources().getDimension(R.dimen.min_max_horiz_spacing)
                                + mMaxTempPaint.measureText(maxTempStr))
                        ,
                        mYOffset + 2 * mLineHeight + (getResources().getDimension(R.dimen.icon_size) * 3/4),
                        mMinTempPaint);


            }
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

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.e(TAG, "onDataChanged Rxd = ");
            for (DataEvent dataEvent : dataEvents){

                if (dataEvent.getType() == DataEvent.TYPE_CHANGED){

                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();

                    String path = dataEvent.getDataItem().getUri().getPath();

                    if (path.equals("/trial")){

                        int temp = dataMap.getInt("temp",23);

                        Log.e(TAG, "Temp Rxd = " + temp);
                    }
                }
            }
        }
    }
}
