package com.example.demotunerhal;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "DemoTunerHal-main";
    private TextView mTextView;
    private TextureView mVideoView;
    private TunerHalControl mTunerHalCtrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = findViewById(R.id.DemoTunerHal);
        mVideoView = findViewById(R.id.TvVideoView);

        //create TunerHalControl
        mTunerHalCtrl = new TunerHalControl();
        if (mTunerHalCtrl == null) {
            throw new NullPointerException("Create TunerHalControl failed!!");
        }

        //set PlatTv/StopTv clicklistener
        Button playTvButton = findViewById(R.id.PlayTv);
        playTvButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Play Tv Button clicked!!");
                        mTextView.setText("Play TV...");
                        mTunerHalCtrl.playTv(mVideoView);
                    }
                }
        );

        Button stopTvButton = findViewById(R.id.StopPlay);
        stopTvButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Stop Play Button clicked!!");
                        mTextView.setText("Stop play...");
                        mTunerHalCtrl.stopPlay(null);
                    }
                }
        );

        Button playVideoThreadButton = findViewById(R.id.PlayVideoThread);
        playVideoThreadButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Play Video Thread Button clicked!!");
                        mTextView.setText("Play Video File...");
                        mTunerHalCtrl.playVideo(mVideoView, true);
                    }
                }
        );

        Button playVideoTimerButton = findViewById(R.id.PlayVideoTimer);
        playVideoTimerButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Play Video Timer Button clicked!!");
                        mTextView.setText("Play Video File...");
                        mTunerHalCtrl.playVideo(mVideoView, false);
                    }
                }
        );

        Button readProgramInfoButton = findViewById(R.id.ReadProgramInfo);
        readProgramInfoButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Read Program Info Button clicked!!");
                        mTextView.setText("Read Program Info...");
                        mTunerHalCtrl.readProgramInfo(mVideoView);
                    }
                }
        );

    }

    @Override
    protected void onDestroy () {
        Log.d(TAG, "Called mTunerHalCtrl.stopPlay()!!");
        mTunerHalCtrl.stopPlay(null);
        super.onDestroy();
    }
}
