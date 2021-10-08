package com.example.demotunerhal;

import android.animation.TimeAnimator;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;

import java.io.IOException;

class TunerHalControl {
    private static final String TAG = "TunerHalControl";

    private static final int MSG_SET_TV_TUNER = 0;
    private static final int MSG_PLAY_TV_CHANNEL = 1;
    private static final int MSG_PLAY_VIDEO_FILE = 2;
    private static final int MSG_STOP_PLAY = 3;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private TextureView mVideoView;
    private Context mContext = null;


    private class MyHadler extends Handler {
        private TimeAnimator mTimeAnimator = null;
        private MediaExtractor mExtractor = null;
        private MediaCodecWrapper mCodecWrapper;
        //private Tuner mTuner;

        private static final int CASE_PLAY_TV = 0;
        private static final int CASE_PLAY_FILE = 1;
        private int playCase = CASE_PLAY_TV;

        public MyHadler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_SET_TV_TUNER:
                    Log.d(TAG, "case MSG_SET_TV_TUNER");
                    break;
                case MSG_PLAY_TV_CHANNEL:
                    Log.d(TAG, "case MSG_PLAY_TV_CHANNEL");
                    onPlayTv();
                    break;
                case MSG_PLAY_VIDEO_FILE:
                    Log.d(TAG, "case MSG_PLAY_VIDEO_FILE");
                    onPlayVideo();
                    break;
                case MSG_STOP_PLAY:
                    Log.d(TAG, "case MSG_STOP_PLAY");
                    onStopPlay();
                    break;
                default:
                    Log.d(TAG, "Why in [default] case??");
                    break;
            }

        }

        public void onPlayTv() {
            Log.i(TAG, "onPlayTv()");
            playCase = CASE_PLAY_TV;
        }

        public void onPlayVideo() {
            Log.i(TAG, "onPlayVideo()");

            playCase = CASE_PLAY_FILE;
            mTimeAnimator = new TimeAnimator();
            if (mTimeAnimator == null) {
                throw new NullPointerException("Create TimeAnimator failed!!");
            }

            mExtractor = new MediaExtractor();
            if (mExtractor == null) {
                throw new NullPointerException("Create MediaExtractor failed!!");
            }

            startPlayback();
        }

        public void onStopPlay() {
            Log.i(TAG, "onStopPlay()");

            if(mTimeAnimator != null && mTimeAnimator.isRunning()) {
                mTimeAnimator.end();
            }

            if (mCodecWrapper != null ) {
                mCodecWrapper.stopAndRelease();
                mCodecWrapper = null;
                mExtractor.release();
            }
        }

        private void startPlayback() {
            // Construct a URI that points to the video resource that we want to play
            //[NOTE]
            // rename xxx.ts to xxx.mp4, otherwise cannot find the video file
            String uri = "android.resource://"
                    + mContext.getPackageName() + "/"
                    + R.raw.tv_ts;
            Log.i(TAG, "[startPlayback] Play fixed file:" + uri);

            Uri videoUri = Uri.parse(uri);

            try {

                // BEGIN_INCLUDE(initialize_extractor)
                mExtractor.setDataSource(mContext, videoUri, null);
                int nTracks = mExtractor.getTrackCount();

                // Begin by unselecting all of the tracks in the extractor, so we won't see
                // any tracks that we haven't explicitly selected.
                for (int i = 0; i < nTracks; ++i) {
                    mExtractor.unselectTrack(i);
                }

                // Find the first video track in the stream. In a real-world application
                // it's possible that the stream would contain multiple tracks, but this
                // sample assumes that we just want to play the first one.
                for (int i = 0; i < nTracks; ++i) {
                    // Try to create a video codec for this track. This call will return null if the
                    // track is not a video track, or not a recognized video format. Once it returns
                    // a valid MediaCodecWrapper, we can break out of the loop.
                    mCodecWrapper = MediaCodecWrapper.fromVideoFormat(
                            mExtractor.getTrackFormat(i),
                            new Surface(mVideoView.getSurfaceTexture())
                    );

                    if (mCodecWrapper != null) {
                        mExtractor.selectTrack(i);
                        break;
                    }
                }
                // END_INCLUDE(initialize_extractor)

                // By using a {@link TimeAnimator}, we can sync our media rendering commands with
                // the system display frame rendering. The animator ticks as the {@link Choreographer}
                // receives VSYNC events.
                mTimeAnimator.setTimeListener(new TimeAnimator.TimeListener() {
                    @Override
                    public void onTimeUpdate(final TimeAnimator animation,
                                             final long totalTime,
                                             final long deltaTime) {

                        boolean isEos = ((mExtractor.getSampleFlags() & MediaCodec
                                .BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                        // BEGIN_INCLUDE(write_sample)
                        if (!isEos) {
                            // Try to submit the sample to the codec and if successful advance the
                            // extractor to the next available sample to read.
                            boolean result = mCodecWrapper.writeSample(mExtractor, false,
                                    mExtractor.getSampleTime(), mExtractor.getSampleFlags());

                            if (result) {
                                // Advancing the extractor is a blocking operation and it MUST be
                                // executed outside the main thread in real applications.
                                mExtractor.advance();
                            }
                        }
                        // END_INCLUDE(write_sample)

                        // Examine the sample at the head of the queue to see if its ready to be
                        // rendered and is not zero sized End-of-Stream record.
                        MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
                        mCodecWrapper.peekSample(out_bufferInfo);

                        // BEGIN_INCLUDE(render_sample)
                        if (out_bufferInfo.size <= 0 && isEos) {
                            mTimeAnimator.end();
                            mCodecWrapper.stopAndRelease();
                            mCodecWrapper = null;
                            mExtractor.release();
                        } else if (out_bufferInfo.presentationTimeUs / 1000 < totalTime) {
                            // Pop the sample off the queue and send it to {@link Surface}
                            mCodecWrapper.popSample(true);
                        }
                        // END_INCLUDE(render_sample)

                    }
                });

                // We're all set. Kick off the animator to process buffers and render video frames as
                // they become available
                mTimeAnimator.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean hasTuner() {
            return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
        }

        private void openTuner() {
            if (!hasTuner()) {
                Log.e(TAG, "openTuner() failed, check feature: android.hardware.tv.tuner");
                return;
            }

        }

        private void closeTuner() {

        }
    }

    public TunerHalControl() {
        mHandlerThread = new HandlerThread("TunerHal HandlerThread");
        if (mHandlerThread == null) {
            throw new NullPointerException("Create TunerHal HandlerThread failed!!");
        }

        //start mHandlerThread
        mHandlerThread.start();

        mHandler = new MyHadler(mHandlerThread.getLooper());
        if (mHandler == null) {
            throw new NullPointerException("Create Handler failed!!");
        }
    }

    public void playTv(TextureView view) {
        mVideoView = view;
        mContext = view.getContext();
        mHandler.sendEmptyMessage(MSG_PLAY_TV_CHANNEL);
    }

    public void playVideo(TextureView view) {
        mVideoView = view;
        mContext = view.getContext();
        mHandler.sendEmptyMessage(MSG_PLAY_VIDEO_FILE);
    }

    public void stopPlay(TextureView view) {
        mVideoView = view;
        mContext = null;
        mHandler.sendEmptyMessage(MSG_STOP_PLAY);
    }
}
