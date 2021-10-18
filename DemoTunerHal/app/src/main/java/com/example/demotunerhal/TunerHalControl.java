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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

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
    private Surface mSurface;
    private Context mContext = null;
    private boolean mIsPlayThread = true;

    private static final int FILTER_BUFFER_SIZE = 2 * 1024 * 1024;
    private static final int AUDIO_TPID = 484;
    private static final int VIDEO_TPID = 481;
    private static final int FREQUENCY = 195000000;
    private static final int TIMEOUT_US = 100000;
    private static final int NO_TIMEOUT = 0;

    private class MyHadler extends Handler {
        private TimeAnimator mTimeAnimator = null;
        private MediaExtractor mExtractor = null;
        private MediaCodecWrapper mCodecWrapper = null;
        private Tuner mTuner;
        private Filter mVideoFilter;
        private boolean mSkipFirstFrame = false;
        private Thread mPlaybackThread = null;
        private MediaCodec mCodec = null;
        private boolean mUseCodecWrapper = false;
        private Deque<MediaEvent> mVideoEventQueue;
        private static final int STATE_STOP = 0;
        private static final int STATE_PLAY = 1;
        final private Object mStateLock;
        private int mState = STATE_STOP;

        public MyHadler(Looper looper) {
            super(looper);
            mStateLock = new Object();
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
                    if (mSkipFirstFrame == false) {
                        mSkipFirstFrame = true;
                    }
                    Log.i(TAG, "--- mSkipFirstFrame=" + mSkipFirstFrame);
                    break;
                default:
                    Log.d(TAG, "Why in [default] case??");
                    break;
            }

        }

        //------------------------------------------------------------
        public void onPlayVideo() {
            Log.i(TAG, "onPlayVideo(): mIsPlayThread=" + mIsPlayThread);
            synchronized (mStateLock) {
                mState = STATE_PLAY;
            }

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

            synchronized (mStateLock) {
                mState = STATE_STOP;
            }

            if(mTimeAnimator != null && mTimeAnimator.isRunning()) {
                mTimeAnimator.end();
            }
            //stop playback thread before closing codec and extractor
            if (mPlaybackThread != null) {
                Log.i(TAG, "Join mPlaybackThread ...");
                try {
                    mPlaybackThread.join();
                    mPlaybackThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

            //clear video filter
            if (mVideoFilter != null) {
                mVideoFilter.flush();
                mVideoFilter.stop();
                mVideoFilter.close();
                mVideoFilter = null;
            }
            //close tuner
            if (mTuner != null) {
                mTuner.cancelTuning();
                mTuner.close();
                mTuner = null;
            }
            //clear event queue
            mVideoEventQueue = null;

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
                            mSurface
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
                        if (mSkipFirstFrame == true) {
                            Log.i(TAG, "99999999999999999999999999999 skip 1st frame!!");
                            mSkipFirstFrame = false;
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
                            mSurface
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
                            if (mSkipFirstFrame == true) {
                                Log.i(TAG, "99999999999999999999999999999 skip 1st frame!!");
                                mSkipFirstFrame = false;
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
    //------------------------------------------------------------
    //------------------------------------------------------------
        public void onPlayTv() {
            Log.i(TAG, "onPlayTv()");
            synchronized (mStateLock) {
                mState = STATE_PLAY;
            }

            if (!mUseCodecWrapper) {
                initCodec(MediaFormat.MIMETYPE_VIDEO_MPEG2, 1920, 1080);
            } else {
                initCodecWrapper(MediaFormat.MIMETYPE_VIDEO_MPEG2, 1920, 1080);
            }

            openTuner(FREQUENCY);
            openVideoFilter(MediaFormat.MIMETYPE_VIDEO_MPEG2);

            //create decode thread
            /*
            mDecoderThread =
                    new Thread(
                            this::decodeThread,
                            "DemoTunerHal-decode-thread");
            mDecoderThread.start();*/
        }

        private boolean initCodec(String mime, int width, int high) {
            Log.i(TAG, "initCodec()");

            if (mCodec != null) {
                mCodec.release();
                mCodec = null;
            }

            try {
                mCodec = MediaCodec.createDecoderByType(mime);
                MediaFormat mf = MediaFormat.createVideoFormat(mime, width, high);
                mCodec.configure(mf, mSurface, null, 0);
                //start codec
                mCodec.start();
            } catch (IOException e) {
                Log.e(TAG, "[initCodec] Error: " + e.getMessage());
            }

            if (mCodec == null) {
                Log.e(TAG, "[initCodec] null codec!");
                return false;
            }
            return true;
        }

        private boolean initCodecWrapper(String mime, int width, int high) {
            Log.i(TAG, "initCodecWrapper()");

            if (mCodecWrapper != null ) {
                mCodecWrapper.stopAndRelease();
                mCodecWrapper = null;
                //mExtractor.release();
            }

            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }

            MediaFormat mf = MediaFormat.createVideoFormat(mime, width, high);
            Log.d(TAG, "[openVideoFilter]: mime:" + mime + ", media format:" + mf.toString());

            try {
                mCodecWrapper = MediaCodecWrapper.fromVideoFormat(mf, mSurface);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (mCodecWrapper == null) {
                Log.e(TAG, "[initCodecWrapper] null codecWrapper!");
                return false;
            }
            return true;
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
                            //different thread, check is running first
                             synchronized (mStateLock) {
                                if (mState != STATE_PLAY) {
                                    Log.d(TAG, "getFilterCallback(): Playback is STOPPED!!");
                                    return;
                                }
                                //queue to MediaCodec
                                if (!mUseCodecWrapper) {
                                    mVideoEventQueue.add((MediaEvent)e);
                                    runDecodeLoop((MediaEvent)e);
                                } else {
                                    queueInput((MediaEvent)e);
                                }
                            }
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
            //clear before new setup
            if (mVideoFilter != null) {
                mVideoFilter.flush();
                mVideoFilter.stop();
                mVideoFilter.close();
                mVideoFilter = null;
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
            //start video filter
            mVideoFilter.start();

            //create video event queue
            mVideoEventQueue = new ArrayDeque<>();
        }

        private void queueInput(MediaEvent me) {
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
                 e.printStackTrace();
            }

            linearBlock.recycle();
            me.release();

            MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
            mCodecWrapper.peekSample(out_bufferInfo);
        }

        private void queueInputBuffer_wait(MediaEvent me) {
            //check input buffer
            int index;
            while ((index = mCodec.dequeueInputBuffer(NO_TIMEOUT)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }

            Log.d(TAG, "dequeueInputBuffer(): got free input index= " + index);
            ByteBuffer inputBuffer;
            if ((inputBuffer = mCodec.getInputBuffer(index)) == null) {
                throw new RuntimeException("Null decoder input buffer");
            }

            ByteBuffer sampleData = me.getLinearBlock().map();
            int sampleSize = (int) me.getDataLength();
            long pts = me.getPts();

            Log.d(TAG, " me.getAvDataId()= " + me.getAvDataId());
            Log.d(TAG, " me.getDataLength()= " + sampleSize);
            Log.d(TAG, " me.getPts()= " + pts);

            // fill codec input buffer
            inputBuffer.clear();
            inputBuffer.put(sampleData);
            mCodec.queueInputBuffer(index, 0, sampleSize, pts, 0);

            //release ion buffer
            me.getLinearBlock().recycle();
            me.release();
        }

        private void releaseOutputBuffer_wait() {
            //check output buffer
            int index;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while ((index = mCodec.dequeueOutputBuffer(info, NO_TIMEOUT)) !=  MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (index >= 0) {
                    mCodec.releaseOutputBuffer(index, true);
                    Log.d(TAG, "dequeueOutputBuffer(): got free output index= " + index);
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = mCodec.getOutputFormat();
                    Log.d(TAG, "dequeueOutputBuffer(): Output format changed: " + format);
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "dequeueOutputBuffer(): Output buffer changed!");
                } else {
                    throw new IllegalStateException("Unknown status from dequeueOutputBuffer(): " + index);
                }
            }
        }

        private boolean queueInputBuffer(MediaEvent me, long timeoutUs, int timeOutCount) {
            //check input buffer
            int index = MediaCodec.INFO_TRY_AGAIN_LATER;
            int count = 0;
            while (count++ < timeOutCount) {
                try {
                    if ((index = mCodec.dequeueInputBuffer(timeoutUs)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                } catch (MediaCodec.CodecException e) {
                     e.printStackTrace();
                }
            }
            Log.d(TAG, "dequeueInputBuffer() =" + index + ", count=" + count);

            if (index < 0) {
                return false;
            }

            ByteBuffer inputBuffer;
            if ((inputBuffer = mCodec.getInputBuffer(index)) == null) {
                throw new RuntimeException("Null decoder input buffer");
            }

            ByteBuffer sampleData = me.getLinearBlock().map();
            int sampleSize = (int) me.getDataLength();
            long pts = me.getPts();

            Log.d(TAG, " me.getAvDataId()= " + me.getAvDataId());
            Log.d(TAG, " me.getDataLength()= " + sampleSize);
            Log.d(TAG, " me.getPts()= " + pts);

            // fill codec input buffer
            inputBuffer.clear();
            inputBuffer.put(sampleData);
            mCodec.queueInputBuffer(index, 0, sampleSize, pts, 0);

            //release ion buffer
            me.getLinearBlock().recycle();
            me.release();
            return true;
        }

        private void releaseOutputBuffer(long timeoutUs, int timeOutCount) {
            //check output buffer
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = MediaCodec.INFO_TRY_AGAIN_LATER;
            int count = 0;
            while (count++ < timeOutCount) {
                try {
                    if ((index = mCodec.dequeueOutputBuffer(info, timeoutUs)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                } catch (MediaCodec.CodecException e) {
                     e.printStackTrace();
                }
            }

            Log.d(TAG, "dequeueOutputBuffer() =" + index + ", count=" + count);

            if (index >= 0) {
                mCodec.releaseOutputBuffer(index, true);
                Log.d(TAG, "dequeueOutputBuffer(): got free output index= " + index);
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mCodec.getOutputFormat();
                Log.d(TAG, "dequeueOutputBuffer(): Output format changed: " + format);
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "dequeueOutputBuffer(): Output buffer changed!");
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "dequeueOutputBuffer(): time out!");
            } else {
                throw new IllegalStateException("Unknown status from dequeueOutputBuffer(): " + index);
            }
        }

        private void runDecodeLoop(MediaEvent me) {
            //check is running
            synchronized (mStateLock) {
                if (mState != STATE_PLAY) {
                    Log.d(TAG, "runDecodeLoop(): Playback is STOPPED!!");
                    return;
                }
            }
            //check event queue and start decode process
             if (!mVideoEventQueue.isEmpty()) {
                if (queueInputBuffer(mVideoEventQueue.getFirst(), 1000, 10) == true) {
                    //remove consumed event
                    mVideoEventQueue.pollFirst();
                }
                releaseOutputBuffer(1000, 10);
            }
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
        mSurface = new Surface(mVideoView.getSurfaceTexture());
        mHandler.sendEmptyMessage(MSG_PLAY_TV_CHANNEL);
    }

    public void playVideo(TextureView view, boolean bPlayThread) {
        mVideoView = view;
        mContext = view.getContext();
        mSurface = new Surface(mVideoView.getSurfaceTexture());
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
        mSurface = new Surface(mVideoView.getSurfaceTexture());
        mHandler.sendEmptyMessage(MSG_SKIP_FIRST_FRAME);
    }
}
