package com.luke.recorddemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


public class MainActivity extends Activity implements
        View.OnTouchListener{

    private static final String TAG = MainActivity.class.toString();

    public static final int RECORD_MODE_CLICK = 1;
    public static final int RECORD_MODE_HOLD = 2;

    private TextView mTvRecordDuration,mTvRecordPath;

    private ImageView mRecordCancel;
    private Button mBtnHoldRecording;

    private MediaRecorder mMediaRecorder;
    private boolean mIsRecording;
    private String mRecordingFilePath;

    private RecordTimerTask mRecordTimerTask;
    private long mRecordDuration =0;

    /** 語音訊息最多可以錄製多久, 單位是秒 **/
    public static final long MAX_RECORD_DURATION_IN_SECOND = 10 * 1000;
    /** 語音訊息最短要錄製多久, 單位是秒 **/
    public static final long MIN_RECORD_DURATION_IN_SECOND = 1 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvRecordDuration = (TextView)findViewById(R.id.tv_duration);
        mTvRecordPath = (TextView)findViewById(R.id.tv_path);

        mRecordCancel = (ImageView)findViewById(R.id.iv_record_cancel);
        mBtnHoldRecording = (Button)findViewById(R.id.btn_record);
        mBtnHoldRecording.setOnTouchListener(this);
    }

    @Override
    protected void onStop() {
        if (mRecordTimerTask != null) {
            mRecordTimerTask.cancel(true);
            mRecordTimerTask = null;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
        }
        super.onStop();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        final Rect buttonBound = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        final boolean isHoverRecordButton = buttonBound.contains(v.getLeft() + (int) event.getX(),
                v.getTop() + (int) event.getY());

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                startRecordingByHold();
                mRecordCancel.setVisibility(View.VISIBLE);
                mRecordCancel.setImageResource(R.drawable.ic_chat_recorder);

                mBtnHoldRecording.setText(R.string.loosen_end);
                mBtnHoldRecording.setBackgroundResource(R.drawable.shape_btn_record_press);

                cleanupInfo();
                return true;

            case MotionEvent.ACTION_UP:
                if (mIsRecording) {

                    // 在錄音按鈕上放開手指, 代表發送語音訊息
                    if (isHoverRecordButton) {
                        stopRecording(false);
                    }else {
                        stopRecording(true);
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isHoverRecordButton) {
                    mRecordCancel.setImageResource(R.drawable.ic_chat_recorder);
                } else {
                    mRecordCancel.setImageResource(R.drawable.ic_chat_recorder_cancel);
                }
                return true;
        }
        return false;
    }

    /**
     * 詢問使用者是否要傳送語音訊息
     */
    private void promptSendVoiceClip() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.recording_limit_30s)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendRecording(mRecordingFilePath);
                    }
                }).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        })
                .setCancelable(false)
                .show();
    }

    private void cleanupInfo() {
        mTvRecordPath.setText("");
        mTvRecordDuration.setText("");
    }

    /**
     * 傳送語音訊息
     */
    private void sendRecording(String audioFilePath) {

        if (mRecordDuration<MIN_RECORD_DURATION_IN_SECOND){
            Toast.makeText(this, R.string.recording_less_1s,Toast.LENGTH_SHORT).show();
        }else {
            mTvRecordPath.setText(audioFilePath);
        }
    }



    /**
     * 按住錄音鍵錄音, 開始錄音
     */
    private void startRecordingByHold() {
        if (mIsRecording) {
            return;
        }

        startRecording();

        // Cancel last task if exist
        if (mRecordTimerTask != null) {
            mRecordTimerTask.cancel(true);
            mRecordTimerTask = null;
        }

        // Start record timer
        mRecordTimerTask = new RecordTimerTask(new OnRecordTimerEventListener() {

            @Override
            public void onStartTick() {
                mTvRecordDuration.setText("");
            }

            @Override
            public void onUpdateTick(RecorderTimerTick tick) {
                mRecordDuration = tick.durationInMillisecond;
                mTvRecordDuration.setText(mRecordDuration/1000+"");

                if (mRecordDuration>=MAX_RECORD_DURATION_IN_SECOND){
                    stopRecording(false);
                }
            }

            @Override
            public void onStopTick() {
            }

        });
        mRecordTimerTask.execute();
    }

    /**
     * 開始錄音
     */
    protected void startRecording() {
        final String folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
                .getAbsolutePath();
        File dir = new File(folder);
        dir.mkdirs();

        final String tempFileName = getPackageName().replace(".", "_") + "_tmp.m4a";

        mRecordingFilePath = new StringBuilder().append(folder)
                .append(File.separator).append(tempFileName)
                .toString();

        Log.v(TAG, "save temp voice mp4 to " + mRecordingFilePath);

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mRecordingFilePath);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mMediaRecorder.prepare();
            mIsRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.start();
    }

    /**
     * 停止錄音
     */
    private void stopRecording(boolean isCancel) {

        mBtnHoldRecording.setText(R.string.click_talk);
        mBtnHoldRecording.setBackgroundResource(R.drawable.shape_btn_record);

        mRecordCancel.setVisibility(View.GONE);

        if (!mIsRecording) {
            return;
        }

        mIsRecording = false;
        mRecordTimerTask.cancel(true);
        try {
            mMediaRecorder.stop();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        mMediaRecorder.release();

        if (isCancel){
            cleanupInfo();
        }else {
            if (mRecordDuration>=MAX_RECORD_DURATION_IN_SECOND){
                promptSendVoiceClip();
            }else {
                sendRecording(mRecordingFilePath);
            }
        }
    }

    /**
     * Callback event listener for {@link RecordTimerTask}
     */
    private interface OnRecordTimerEventListener {
        /** 開始計時錄音的秒數 **/
        public void onStartTick();

        /** 更新 UI 顯示目前的錄音秒數 **/
        public void onUpdateTick(RecorderTimerTick tick);

        /** 停止計時錄音的秒數 **/
        public void onStopTick();
    };

    /**
     * Update progress for {@link RecordTimerTask}
     */
    private static class RecorderTimerTick {
        public long durationInMillisecond;
    };

    /**
     * 紀錄按住錄音, 總共錄了多久
     */
    private class RecordTimerTask extends AsyncTask<Void, RecorderTimerTick, Void> {

        private OnRecordTimerEventListener mOnRecorderTimerListener;

        public RecordTimerTask(OnRecordTimerEventListener listener) {
            mOnRecorderTimerListener = listener;
        }

        @Override
        public void onPreExecute() {
            mOnRecorderTimerListener.onStartTick();
        }

        @Override
        protected Void doInBackground(Void... params) {
            RecorderTimerTick tick = new RecorderTimerTick();
            SimpleDateFormat sdf = new SimpleDateFormat("mm:ss", Locale.getDefault());

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(0);

            tick.durationInMillisecond = c.getTimeInMillis();
            publishProgress(tick);

            while (mIsRecording && !isCancelled()) {
                try {
                    Thread.sleep(1000);
                    c.add(Calendar.MILLISECOND, 1000);

                    tick.durationInMillisecond = c.getTimeInMillis();
                    publishProgress(tick);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(RecorderTimerTick... tick) {
            mOnRecorderTimerListener.onUpdateTick(tick[0]);
        }

        @Override
        public void onPostExecute(Void params) {
            mOnRecorderTimerListener.onStopTick();
        }
    }

}
