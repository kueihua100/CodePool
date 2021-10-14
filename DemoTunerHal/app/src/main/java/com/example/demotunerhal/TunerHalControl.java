package com.example.demotunerhal;

import android.animation.TimeAnimator;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

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
import java.util.concurrent.Executor;
import java.nio.ByteBuffer;

import android.media.tv.tuner.*;
import android.media.tv.tuner.filter.*;
import android.media.tv.tuner.frontend.*;

class TunerHalControl {
    private static final String TAG = "DemoTunerHal-ctrl";

    private static final int MSG_SET_TV_TUNER = 0;
    private static final int MSG_PLAY_TV_CHANNEL = 1;
    private static final int MSG_PLAY_VIDEO_FILE = 2;
    private static final int MSG_STOP_PLAY = 3;
    private static final int MSG_SKIP_FIRST_FRAME = 4;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private TextureView mVideoView;
    private Context mContext = null;
    private boolean mIsPlayThread = true;

    private static final int FILTER_BUFFER_SIZE = 2 * 1024 * 1024;
    private static final int AUDIO_TPID = 484;
    private static final int VIDEO_TPID = 481;
    private static final int FREQUENCY = 195000000;

    private class MyHadler extends Handler {
        private TimeAnimator mTimeAnimator = null;
        private MediaExtractor mExtractor = null;
        private MediaCodecWrapper mCodecWrapper;
        private Tuner mTuner;
        private Filter mVideoFilter;
        private boolean skipFirstFrame = false;
        private Thread mPlaybackThread = null;


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
                case MSG_SKIP_FIRST_FRAME:
                    Log.d(TAG, "case MSG_SKIP_FIRST_FRAME");
                    if (skipFirstFrame == false) {
                        skipFirstFrame = true;
                    }
                    Log.i(TAG, "--- skipFirstFrame=" + skipFirstFrame);
                    break;
                default:
                    Log.d(TAG, "Why in [default] case??");
                    break;
            }

        }

        //------------------------------------------------------------
        public void onPlayVideo() {
            Log.i(TAG, "onPlayVideo(): mIsPlayThread=" + mIsPlayThread);

            mTimeAnimator = new TimeAnimator();
            if (mTimeAnimator == null) {
                throw new NullPointerException("Create TimeAnimator failed!!");
            }

            mExtractor = new MediaExtractor();
            if (mExtractor == null) {
                throw new NullPointerException("Create MediaExtractor failed!!");
            }

            if (mIsPlayThread) {
                mPlaybackThread = new Thread(this::playbackThreadLoop, "playback-thread-loop");
                mPlaybackThread.start();
            } else {
                startPlayback();
            }
        }

        public void onStopPlay() {
            Log.i(TAG, "onStopPlay()");

            if(mTimeAnimator != null && mTimeAnimator.isRunning()) {
                mTimeAnimator.end();
            }

            if (mCodecWrapper != null ) {
                mCodecWrapper.stopAndRelease();
                mCodecWrapper = null;
                //mExtractor.release();
            }

            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }

            if (mPlaybackThread != null) {
                Log.i(TAG, "Join mPlaybackThread ...");
                try {
                    mPlaybackThread.join();
                    mPlaybackThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void playbackThreadLoop() {
            // Construct a URI that points to the video resource that we want to play
            //[NOTE]
            // rename xxx.ts to xxx.mp4, otherwise cannot find the video file
            String uri = "android.resource://"
                    + mContext.getPackageName() + "/"
                    + R.raw.tv_ts;
            uri = "/data/local/tmp/atsc_cc.ts";
            Log.i(TAG, "[startPlayback] Play fixed file:" + uri);

            Uri videoUri = Uri.parse(uri);

            try {
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

                while (!Thread.interrupted()) {
                    boolean isEos = ((mExtractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                    if (!isEos) {
                        // Try to submit the sample to the codec and if successful advance the
                        // extractor to the next available sample to read.
                        boolean result = false;
                        if (skipFirstFrame == true) {
                            Log.i(TAG, "99999999999999999999999999999 skip 1st frame!!");
                            skipFirstFrame = false;
                            result = true;
                        } else {
                            result = mCodecWrapper.writeSample(mExtractor, false, mExtractor.getSampleTime(), mExtractor.getSampleFlags());
                        }

                        if (result) {
                            // Advancing the extractor is a blocking operation and it MUST be
                            // executed outside the main thread in real applications.
                            mExtractor.advance();
                        }

                        // Examine the sample at the head of the queue to see if its ready to be
                        // rendered and is not zero sized End-of-Stream record.
                        MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
                        mCodecWrapper.peekSample(out_bufferInfo);

                        if (out_bufferInfo.size <= 0 && isEos) {
                            mTimeAnimator.end();
                            mCodecWrapper.stopAndRelease();
                            mCodecWrapper = null;
                            mExtractor.release();
                            mExtractor = null;
                        }
                        // Pop the sample off the queue and send it to {@link Surface}
                        mCodecWrapper.popSample(true);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void startPlayback() {
            // Construct a URI that points to the video resource that we want to play
            //[NOTE]
            // rename xxx.ts to xxx.mp4, otherwise cannot find the video file
            String uri = "android.resource://"
                    + mContext.getPackageName() + "/"
                    + R.raw.tv_ts;
            uri = "/data/local/tmp/atsc_cc.ts";
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
                            boolean result = false;
                            if (skipFirstFrame == true) {
                                Log.i(TAG, "99999999999999999999999999999 skip 1st frame!!");
                                skipFirstFrame = false;
                                result = true;
                            } else {
                                result = mCodecWrapper.writeSample(mExtractor, false,
                                        mExtractor.getSampleTime(), mExtractor.getSampleFlags());
                            }

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
                            mExtractor = null;
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


    //------------------------------------------------------------
        public void onPlayTv() {
            Log.i(TAG, "onPlayTv()");
            openTuner(FREQUENCY);
            openVideoFilter(MediaFormat.MIMETYPE_VIDEO_MPEG2);
        }

        private boolean hasTuner() {
            return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
        }

        private Executor getExecutor() {
            return Runnable::run;
        }

        private FrontendSettings createAtscFrontendSettings(int frequency) {
            Log.d(TAG, "-----tune to frequency: " + frequency);

            return AtscFrontendSettings
                    .builder()
                    .setFrequency(frequency)
                    .setModulation(AtscFrontendSettings.MODULATION_AUTO)
                    .build();
        }

        private FilterConfiguration createVideoConfiguration(int pid) {
            Log.d(TAG, "-----ts PID: " + pid);

            Settings settings = AvSettings
                    .builder(Filter.TYPE_TS, false)
                    .setPassthrough(false)
                    .build();

            return TsFilterConfiguration
                    .builder()
                    .setTpid(pid)
                    .setSettings(settings)
                    .build();
        }

        private void openTuner(int frequency) {
            if (!hasTuner()) {
                Log.e(TAG, "openTuner() failed, check feature: android.hardware.tv.tuner");
                return;
            }
            //new Tuner
            mTuner = new Tuner(mContext, null, 100);
            //set tuner event listener
            mTuner.setOnTuneEventListener(getExecutor(), new OnTuneEventListener() {
                @Override
                public void onTuneEvent(int tuneEvent) {
                    switch (tuneEvent) {
                        case OnTuneEventListener.SIGNAL_LOCKED:
                            Log.d(TAG, "[onTuneEvent]: signal locked");
                            break;
                        case OnTuneEventListener.SIGNAL_LOST_LOCK:
                            Log.d(TAG, "[onTuneEvent]: signal lost");
                            break;
                        case OnTuneEventListener.SIGNAL_NO_SIGNAL:
                            Log.d(TAG, "[onTuneEvent]: no signal");
                            break;
                        default:
                            Log.d(TAG, "[onTuneEvent] " + tuneEvent);
                            break;
                    }
                }
            });

            //start tune
            FrontendSettings settings = createAtscFrontendSettings(frequency);
            mTuner.tune(settings);
        }

        private void closeTuner() {
            if (mTuner != null) {
              mTuner.close();
              mTuner = null;
            }
        }

        private FilterCallback getFilterCallback() {
            return new FilterCallback() {
                @Override
                public void onFilterEvent(Filter filter, FilterEvent[] events) {
                    for (FilterEvent e : events) {
                        if (e instanceof MediaEvent) {
                            //queue to MediaCodec
                            queueInput((MediaEvent)e);
                        } else if (e instanceof SectionEvent) {
                            //testSectionEvent(filter, (SectionEvent) e);
                        } else if (e instanceof TemiEvent) {
                            //testTemiEvent(filter, (TemiEvent) e);
                        } else if (e instanceof TsRecordEvent) {
                            //testTsRecordEvent(filter, (TsRecordEvent) e);
                        }
                    }
                }
                @Override
                public void onFilterStatusChanged(Filter filter, int status) {
                    Log.d(TAG, "onFilterEvent video, status=" + status);
                }
            };
        }

        private void openVideoFilter(String mime) {
            //stop first
            onStopPlay();

            //clear before new setup
            if (mVideoFilter != null) {
                mVideoFilter.flush();
                mVideoFilter.stop();
                mVideoFilter.close();
                mVideoFilter = null;
            }

            MediaFormat mf = MediaFormat.createVideoFormat(mime, 1920, 1080); //MIMETYPE_VIDEO_VP9 : MIMETYPE_VIDEO_MPEG2
            Log.d(TAG, "[openVideoFilter]: mime:" + mime + "media format:" + mf.toString());

            try {
                mCodecWrapper = MediaCodecWrapper.fromVideoFormat(mf, new Surface(mVideoView.getSurfaceTexture()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            //open filter
            mVideoFilter = mTuner.openFilter(
                            Filter.TYPE_TS,
                            Filter.SUBTYPE_VIDEO,
                            FILTER_BUFFER_SIZE,
                            getExecutor(),
                            getFilterCallback());

            FilterConfiguration config = createVideoConfiguration(VIDEO_TPID);
            mVideoFilter.configure(config);
            mVideoFilter.start();
        }

        public void queueInput(MediaEvent me) {
            mCodecWrapper.popSample(true);

            MediaCodec.LinearBlock linearBlock = me.getLinearBlock();
            ByteBuffer esBuffer = linearBlock.map();

            Log.d(TAG, " me.getAvDataId()= " + me.getAvDataId());
            Log.d(TAG, " me.getDataLength()= " + me.getDataLength());
            Log.d(TAG, " me.getPts()= " + me.getPts());

            try {
                boolean res = mCodecWrapper.writeSample(esBuffer, null, me.getPts(), 0);
                Log.d(TAG, " mCodecWrapper.writeSample()= " + res);
            } catch (MediaCodecWrapper.WriteException e) {
            }

            linearBlock.recycle();
            me.release();

            MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
            mCodecWrapper.peekSample(out_bufferInfo);
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

    public void playVideo(TextureView view, boolean bPlayThread) {
        mVideoView = view;
        mContext = view.getContext();
        mIsPlayThread = bPlayThread;
        mHandler.sendEmptyMessage(MSG_PLAY_VIDEO_FILE);
    }

    public void stopPlay(TextureView view) {
        mVideoView = view;
        mContext = null;
        mHandler.sendEmptyMessage(MSG_STOP_PLAY);
    }

    public void skipFirstFrame(TextureView view) {
        mVideoView = view;
        mContext = view.getContext();
        mHandler.sendEmptyMessage(MSG_SKIP_FIRST_FRAME);
    }
}
