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

        Button playVideoButton = findViewById(R.id.PlayVideo);
        playVideoButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Play Video Button clicked!!");
                        mTextView.setText("Play Video File...");
                        mTunerHalCtrl.playVideo(mVideoView);
                    }
                }
        );

        Button skipFirstFrameButton = findViewById(R.id.Skip1stFrame);
        skipFirstFrameButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Skip 1st Frame Button clicked!!");
                        mTextView.setText("Skip 1st Frame...");
                        mTunerHalCtrl.skipFirstFrame(mVideoView);
                    }
                }
        );

    }
}
