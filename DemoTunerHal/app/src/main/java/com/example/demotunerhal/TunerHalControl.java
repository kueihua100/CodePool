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

//------------------------------------------------
//for TvProgramConfig class
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

class TunerHalControl {
    private static final String TAG = "DemoTunerHal-ctrl";

    private static final int MSG_SET_TV_TUNER = 0;
    private static final int MSG_PLAY_TV_CHANNEL = 1;
    private static final int MSG_PLAY_VIDEO_FILE = 2;
    private static final int MSG_STOP_PLAY = 3;
    private static final int MSG_READ_PROGRAM_INFO = 4;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private TextureView mVideoView;
    private Surface mSurface;
    private Context mContext = null;
    private boolean mIsPlayThread = true;

    private static final int VIDEO_WIDTH = 1920;
    private static final int VIDEO_HEIGHT = 1080;
    private static final int FILTER_BUFFER_SIZE = 2 * 1024 * 1024;
    private static final int AUDIO_TPID = 484;
    private static final int VIDEO_TPID = 481;
    private static final int FREQUENCY = 195000000;
    private static final int TIMEOUT_US = 100000;
    private static final int NO_TIMEOUT = 0;

    public class TunerCodec {
        private static final String TAG = "TunerCodec";
        private static final boolean DEBUG = true;
        private String mMediaType;

        private boolean mCodecASyncMode = true;
        private MediaCodec mCodec = null;
        private Deque<MediaEvent> mVideoEventQueue;
        // Indices of the input buffers that are currently available for writing. We'll
        // consume these in the order they were dequeued from the codec.
        private Deque<Integer> mAvailableInputBuffers;
        // Indices of the output buffers that currently hold valid data, in the order
        // they were produced by the codec.
        private Deque<Integer> mAvailableOutputBuffers;
        // Information about each output buffer, by index. Each entry in this array
        // is valid if and only if its index is currently contained in mAvailableOutputBuffers.
        private Deque<MediaCodec.BufferInfo> mOutputBufferInfo;
        private long mVideoStartCurrtTime = 0;
        private long mVideoStartPts = 0;

        private static final int STATE_STOP = 0;
        private static final int STATE_PLAY = 1;
        final private Object mStateLock;
        private int mState = STATE_STOP;
        private Thread mPlaybackThread = null;

        private Tuner mTuner;
        private Filter mVideoFilter;
        private TvProgramConfig mTvProgramConfig;


        public TunerCodec() {
            Log.i(TAG, "------ TunerCodec()");
            mStateLock = new Object();
            mTvProgramConfig = new TvProgramConfig();
        }

        public void setState(int state) {
            synchronized (mStateLock) {
                mState = state;
            }
        }

        public void close () {
            Log.i(TAG, "------ close()");

            //close video codec and video filter
            closeVideo();

            //close tuner
            closeTuner();
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

        public void openTuner() {
            Log.i(TAG, "------ openTuner()");

            if (!hasTuner()) {
                Log.e(TAG, "openTuner() failed, check feature: android.hardware.tv.tuner");
                return;
            }
            //new Tuner
            if (( mTuner = new Tuner(mContext, null, 100)) == null) {
                throw new NullPointerException("Create Tuner()  failed!!");
            }

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
        }

        public void startTune() {
            Log.i(TAG, "------ startTuner(): frequency=" + mTvProgramConfig.mTunerFrequency);

            //start tune
            FrontendSettings settings = createAtscFrontendSettings(mTvProgramConfig.mTunerFrequency);
            mTuner.tune(settings);
        }

        public void closeTuner() {
            if (mTuner != null) {
                Log.i(TAG, "------ closeTuner()");

                mTuner.cancelTuning();
                mTuner.close();
                mTuner = null;
            }
        }

        public boolean openVideo() {
            //create video codec according to mine, width, height
            openVideoCodec(mTvProgramConfig.mVideoFormat, mTvProgramConfig.mVideoWidth, mTvProgramConfig.mVideoHeight);

            //create video filter and video event queue
            openVideoFilter(mTvProgramConfig.mVideoFilterBuffer);

            //open and start decodec thread
            openDecodeThread();

            return true;
        }

        public int startVideo() {
            Log.i(TAG, "------ startVideo(): pid=" + mTvProgramConfig.mVideoPid);

            //start codec
            mCodec.start();

            //start thread
            mPlaybackThread.start();

            //config video filter
            FilterConfiguration config = createVideoConfiguration(mTvProgramConfig.mVideoPid);
            mVideoFilter.configure(config);

            //start video filter
            return mVideoFilter.start();
        }

        public void closeVideo() {
            //close codec
            closeVideoCodec();

            //clear video filter
            closeVideoFilter();

            //close decodec thread
            closeDecodeThread();
        }

        private void openVideoFilter(long filterBufferSize) {
            //clear before new setup
           closeVideoFilter();

            //open filter
            mVideoFilter = mTuner.openFilter(
                            Filter.TYPE_TS,
                            Filter.SUBTYPE_VIDEO,
                            filterBufferSize,
                            getExecutor(),
                            getFilterCallback());

            if (mVideoFilter == null) {
                throw new NullPointerException("mTuner.openFilter() failed!!");
            }

            //create video event queue
            mVideoEventQueue = new ArrayDeque<>();
        }

        private void closeVideoFilter() {
            if (mVideoFilter != null) {
                Log.i(TAG, "closeVideoFilter()  ...");

                mVideoFilter.flush();
                mVideoFilter.stop();
                mVideoFilter.close();
                mVideoFilter = null;
            }

            //clear event queue
            if (mVideoEventQueue != null) {
                mVideoEventQueue = null;
            }
        }

        private FilterCallback getFilterCallback() {
            return new FilterCallback() {
                @Override
                public void onFilterEvent(Filter filter, FilterEvent[] events) {
                    for (FilterEvent e : events) {
                        if (e instanceof MediaEvent) {
                            //queue event & run decode process
                            mVideoEventQueue.add((MediaEvent)e);
                            //runDecodeProcess();
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

        private boolean openVideoCodec(String mime, int width, int height) {
            Log.i(TAG, "openCodec(): mime=" + mime + ", width=" + width + ", height=" + height);
            //check previous resource
            closeVideoCodec();

            try {
                //create  input/output buffer queue
                mAvailableInputBuffers = new ArrayDeque<>();
                mAvailableOutputBuffers = new ArrayDeque<>();
                mOutputBufferInfo = new ArrayDeque<>();

                mCodec = MediaCodec.createDecoderByType(mime);
                MediaFormat mf = MediaFormat.createVideoFormat(mime, width, height);
                //create callback for asynchronous mode
                if (mCodecASyncMode) {
                    mCodec.setCallback(getCodecCallback());
                }

                mCodec.configure(mf, mSurface, null, 0);
                //start codec
                //mCodec.start();
            } catch (IOException e) {
                Log.e(TAG, "[openCodec] Error: " + e.getMessage());
            }

            return true;
        }

        private void closeVideoCodec() {
            if (mCodec != null) {
                Log.i(TAG, "closeVideoCodec()  ...");

                //clear  input/output buffer queue
                mAvailableInputBuffers.clear();
                mAvailableInputBuffers = null;

                mAvailableOutputBuffers.clear();
                mAvailableOutputBuffers = null;

                mOutputBufferInfo.clear();
                mOutputBufferInfo = null;

                //stop & close codec
                mCodec.stop();
                mCodec.release();
                mCodec = null;
            }
        }

        private final MediaCodec.Callback getCodecCallback() {
            return new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    Log.d(TAG, "onInputBufferAvailable(): free input index= " + index);
                    synchronized (mStateLock) {
                        mAvailableInputBuffers.add(index);
                    }
                    //runDecodeProcess();
                }

                @Override
                public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    //ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    //MediaFormat bufferFormat = codec.getOutputFormat(index); // option A
                    //bufferFormat is equivalent to mOutputFormat
                    //outputBuffer is ready to be processed or rendered.
                    if (codec != mCodec) {
                        Log.w(TAG, "got different codec: " + codec);
                        return;
                    }
                    Log.d(TAG, "onOutputBufferAvailable(): free output index= " + index);
                    //mCodec.releaseOutputBuffer(index, true);
                    synchronized (mStateLock) {
                        if (DEBUG) {
                            Log.d(TAG, String.format("onOutputBufferAvailable() (in %s): presentationTimeUs=0x%x (%d)",
                                                        Thread.currentThread().getName(), info.presentationTimeUs, System.currentTimeMillis()));
                        }
                        mOutputBufferInfo.add(info);
                        mAvailableOutputBuffers.add(index);
                    }
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                    //Subsequent data will conform to new format.
                    //Can ignore if using getOutputFormat(outputBufferId)
                    //mOutputFormat = format; // option B
                    Log.d(TAG, "onOutputFormatChanged():  format= " + format);
                    if (codec != mCodec) {
                        Log.w(TAG, "got different codec: " + codec);
                        return;
                    }
                }

                @Override
                public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                    Log.d(TAG, "onError():  e= " + e);
                    if (codec != mCodec) {
                        Log.w(TAG, "got different codec: " + codec);
                        return;
                    }
                }
            };
        }

        private void runDecodeProcess() {
            synchronized (mStateLock) {
                //[NOTE] this function maybe run by TunerHal HandlerThread or tuner Hal's Binder thread
                Log.d(TAG, "run runDecodeProcess() in thread:" + Thread.currentThread().getName());

                if (mState == STATE_STOP) {
                    Log.d(TAG, "Playback is STOPPED, release event and return!!");

                    for (int i=0; i < mVideoEventQueue.size(); i++) {
                        MediaEvent me = mVideoEventQueue.pollFirst();
                        me.release();
                    }
                    return;
                }

                //check input buffer queue and event queue first
                if ((mVideoEventQueue.size() == 0) || (mAvailableInputBuffers.size() == 0)) {
                    Log.d(TAG, "runDecodeProcess():" +
                                      "mVideoEventQueue.size()=" + mVideoEventQueue.size() +
                                      ", mAvailableInputBuffers.size()=" + mAvailableInputBuffers.size());
                    return;
                }

                //fill inputBuffer with valid data
                int index = mAvailableInputBuffers.pollFirst();
                ByteBuffer inputBuffer = mCodec.getInputBuffer(index);
                MediaEvent me = mVideoEventQueue.pollFirst();

                ByteBuffer sampleData = me.getLinearBlock().map();
                int sampleSize = (int) me.getDataLength();
                long pts = me.getPts();

                Log.d(TAG, " me.getAvDataId()= " + me.getAvDataId());
                Log.d(TAG, " me.getDataLength()= " + sampleSize);
                Log.d(TAG, " me.getPts()= " + pts);

                //fill codec input buffer
                inputBuffer.clear();
                inputBuffer.put(sampleData);
                mCodec.queueInputBuffer(index, 0, sampleSize, pts, 0);

                //release ion buffer
                me.getLinearBlock().recycle();
                me.release();
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
        private void openDecodeThread() {
            Log.i(TAG, "openDecodeThread()");
            //create decode thread
            mPlaybackThread =new Thread(this::runDecodeLoop, "TunerCodec-decode-thread");
            if (mPlaybackThread == null) {
                throw new NullPointerException("openDecodeThread() failed!!");
            }

            //start thread
            //mPlaybackThread.start();
        }

        private void closeDecodeThread() {
            if (mPlaybackThread != null) {
                Log.i(TAG, "Join mPlaybackThread ...");
                try {
                    mPlaybackThread.interrupt();
                    mPlaybackThread.join();
                    mPlaybackThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void runDecodeLoop() {
            //init start time
            mVideoStartCurrtTime = 0;
            mVideoStartPts = 0;

            while (!Thread.interrupted()) {
                //check event queue and start decode process
                /*
                 if (!mVideoEventQueue.isEmpty()) {
                    if (queueInputBuffer(mVideoEventQueue.getFirst(), 1000, 10) == true) {
                        //remove consumed event
                        mVideoEventQueue.pollFirst();
                    }
                    releaseOutputBuffer(1000, 10);
                }*/

                //check output buffer is ready to rendor
                synchronized (mStateLock) {
                    if (!mAvailableOutputBuffers.isEmpty()) {
                        MediaCodec.BufferInfo bufferInfo = mOutputBufferInfo.getFirst();

                        if ((mVideoStartCurrtTime == 0) && (mVideoStartPts == 0)) {
                            mVideoStartCurrtTime = System.currentTimeMillis();
                            mVideoStartPts = bufferInfo.presentationTimeUs;
                            mOutputBufferInfo.pollFirst();
                            mCodec.releaseOutputBuffer(mAvailableOutputBuffers.pollFirst(), true);
                        } else {
                            //check the time stamp
                            long ptsDiff = bufferInfo.presentationTimeUs - mVideoStartPts;
                            long currentTimeDiff = System.currentTimeMillis() - mVideoStartCurrtTime;
                            if (ptsDiff <= currentTimeDiff) {
                                mOutputBufferInfo.pollFirst();
                                mCodec.releaseOutputBuffer(mAvailableOutputBuffers.pollFirst(), true);
                            } else {
                                Log.d(TAG, String.format("runDecodeLoop(): ptsDiff=%d, currentTimeDiff=%d", ptsDiff, currentTimeDiff));
                            }
                        }
                    }
                }

                //check input buffer queue and event queue
                synchronized (mStateLock) {
                    if (mVideoEventQueue.isEmpty() || mAvailableInputBuffers.isEmpty()) {
                        if (DEBUG) {
                            Log.d(TAG, "runDecodeLoop():" +
                                              "mVideoEventQueue.size()=" + mVideoEventQueue.size() +
                                              ", mAvailableInputBuffers.size()=" + mAvailableInputBuffers.size());
                        }
                        continue;
                    }
                }

                //fill inputBuffer with valid data
                int index = mAvailableInputBuffers.pollFirst();
                ByteBuffer inputBuffer = mCodec.getInputBuffer(index);
                MediaEvent me = mVideoEventQueue.pollFirst();

                if (DEBUG) {
                    Log.d(TAG, String.format("runDecodeLoop(): getAvDataId()=%d, getDataLength()=%d, getPts()=%d (%d)",
                                                            me.getAvDataId(), me.getDataLength(), me.getPts(), me.getPts()/90));
                }
                //fill codec input buffer
                inputBuffer.clear();
                inputBuffer.put(me.getLinearBlock().map());
                mCodec.queueInputBuffer(index, 0, (int) me.getDataLength(), me.getPts()/90, 0);

                //release ion buffer
                me.getLinearBlock().recycle();
                me.release();
            }

            //decode thread stop, clear not used media event
            for (int i=0; i < mVideoEventQueue.size(); i++) {
                MediaEvent me = mVideoEventQueue.pollFirst();
                me.release();
            }
        }

        public class TvProgramConfig {
            private static final boolean DEBUG = false;

            //[NOTE] Needs to set sepolicy as permissive: setenforce 0
            private static final String PATH_TO_TV_CONFIG_XML =  "/data/local/tmp/tvProgramConfig.xml";
            public int mTunerFrequency = 195000000;
            public int mVideoPid = 481;
            public String mVideoFormat = MediaFormat.MIMETYPE_VIDEO_MPEG2;
            public int mVideoWidth = 1920;
            public int mVideoHeight = 1080;
            public int mVideoFilterBuffer = 2*1024*1024;
            public int mVideoFreeRun = 1;
            public int mAudioPid = 484;
            public String mAudioFormat = MediaFormat.MIMETYPE_AUDIO_AC3;
            public int mAudioFilterBuffer = 2*1024*1024;
            //xml format:
            //<?xml version="1.0" encoding="UTF-8"?>
            //<config version="1.0" xmlns:xi="http://www.w3.org/2001/XMLSchema">
            //    <Tuner frequency="195000000" />
            //    <Video pid="481" format="video/mpeg2" width="1920" height="1080" filterBuffer="2097152" freeRun="1" />
            //    <Audio pid="484" format="audio/ac3" filterBuffer="2097152" />
            //</config>

            public TvProgramConfig() {
                parse();
            }

            public void parse() {
                File file = new File(PATH_TO_TV_CONFIG_XML);
                if (file.exists()) {
                    try {
                        InputStream in = new FileInputStream(file);
                        parseInternal(in);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading file: " + file, e);
                    } catch (XmlPullParserException e) {
                        Log.e(TAG, "Unable to parse file: " + file, e);
                    }
                } else {
                    Log.i(TAG, "No tv program config file,  using default setting: ");
                }

                String string = String.format("Tuner (frequency=%d)", mTunerFrequency);
                Log.i(TAG, string);
                string = String.format("Video (pid=%d, format=%s, width=%d, height=%d, filterBuffer=0x%x, freeRun=%d)",
                                        mVideoPid, mVideoFormat, mVideoWidth, mVideoHeight, mVideoFilterBuffer, mVideoFreeRun);
                Log.i(TAG, string);
                string = String.format("Audio (pid=%d, format=%s, filterBuffer=0x%x)", mAudioPid, mAudioFormat, mAudioFilterBuffer);
                Log.i(TAG, string);
            }

            protected void parseInternal(InputStream in)
                    throws IOException, XmlPullParserException {
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    parser.setInput(in, null);
                    parser.nextTag();
                    readConfig(parser);
                    in.close();
                } catch (IOException | XmlPullParserException e) {
                    throw e;
                }
            }

            private void readConfig(XmlPullParser parser)
                    throws XmlPullParserException, IOException {
                parser.require(XmlPullParser.START_TAG, null, "config");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String name = parser.getName();
                    if (name.equals("Tuner")) {
                        mTunerFrequency =  Integer.valueOf(parser.getAttributeValue(null, "frequency"));
                        parser.nextTag();
                        parser.require(XmlPullParser.END_TAG, null, name);
                    } else  if (name.equals("Video")) {
                        mVideoPid = Integer.valueOf(parser.getAttributeValue(null, "pid"));
                        mVideoFormat = parser.getAttributeValue(null, "format");
                        mVideoWidth = Integer.valueOf(parser.getAttributeValue(null, "width"));
                        mVideoHeight =  Integer.valueOf(parser.getAttributeValue(null, "height"));
                        mVideoFilterBuffer =  Integer.valueOf(parser.getAttributeValue(null, "filterBuffer"));
                        parser.nextTag();
                        parser.require(XmlPullParser.END_TAG, null, name);
                    } else  if (name.equals("Audio")) {
                        mAudioPid = Integer.valueOf(parser.getAttributeValue(null, "pid"));
                        mAudioFormat = parser.getAttributeValue(null, "format");
                        mAudioFilterBuffer =  Integer.valueOf(parser.getAttributeValue(null, "filterBuffer"));
                        parser.nextTag();
                        parser.require(XmlPullParser.END_TAG, null, name);
                    } else {
                        skip(parser);
                    }
                }
            }

            private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    throw new IllegalStateException();
                }
                int depth = 1;
                while (depth != 0) {
                    switch (parser.next()) {
                        case XmlPullParser.END_TAG:
                            depth--;
                            break;
                        case XmlPullParser.START_TAG:
                            depth++;
                            break;
                    }
                }
            }
        }
    }

    private class MyHadler extends Handler {
        private TimeAnimator mTimeAnimator = null;
        private MediaExtractor mExtractor = null;
        private MediaCodecWrapper mCodecWrapper = null;
        private Thread mPlaybackThread = null;
        private static final int STATE_STOP = 0;
        private static final int STATE_PLAY = 1;
        private TunerCodec mTunerCodec = null;

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
                case MSG_READ_PROGRAM_INFO:
                    Log.d(TAG, "case MSG_READ_PROGRAM_INFO");
                    onReadProgramConfig();
                    break;
                default:
                    Log.d(TAG, "Why in [default] case??");
                    break;
            }

        }

        //------------------------------------------------------------
        public void onPlayVideo() {
            Log.i(TAG, "onPlayVideo(): mIsPlayThread=" + mIsPlayThread);

            if (mIsPlayThread) {
                mPlaybackThread = new Thread(this::playbackThreadLoop, "playback-thread-loop");
                mPlaybackThread.start();
            } else {
                startPlayback();
            }
        }

        public void onStopPlay() {
            Log.i(TAG, "onStopPlay()");

            if (mTunerCodec != null) {
                mTunerCodec.setState(STATE_STOP);
                mTunerCodec.close();
                mTunerCodec = null;
            }

            //stop playback thread before closing codec and extractor
            if (mPlaybackThread != null) {
                Log.i(TAG, "Join mPlaybackThread ...");
                try {
                    mPlaybackThread.interrupt();
                    mPlaybackThread.join();
                    mPlaybackThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //close codec wrapper related
            if(mTimeAnimator != null && mTimeAnimator.isRunning()) {
                mTimeAnimator.end();
            }

            if (mCodecWrapper != null ) {
                Log.i(TAG, "closeCodecWrapper()  ...");

                mCodecWrapper.stopAndRelease();
                mCodecWrapper = null;
                //mExtractor.release();
            }

            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
        }

        private void playbackThreadLoop() {
            mTimeAnimator = new TimeAnimator();
            if (mTimeAnimator == null) {
                throw new NullPointerException("Create TimeAnimator failed!!");
            }

            mExtractor = new MediaExtractor();
            if (mExtractor == null) {
                throw new NullPointerException("Create MediaExtractor failed!!");
            }

            // Construct a URI that points to the video resource that we want to play
            //[NOTE]
            //1. rename xxx.ts to xxx.mp4, otherwise cannot find the video file from android.resource://
            //2. from /data/local/tmp/xxx.ts ==>  Needs to set sepolicy as permissive: setenforce 0
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
                        boolean result = mCodecWrapper.writeSample(mExtractor, false, mExtractor.getSampleTime(), mExtractor.getSampleFlags());

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
            mTimeAnimator = new TimeAnimator();
            if (mTimeAnimator == null) {
                throw new NullPointerException("Create TimeAnimator failed!!");
            }

            mExtractor = new MediaExtractor();
            if (mExtractor == null) {
                throw new NullPointerException("Create MediaExtractor failed!!");
            }

            // Construct a URI that points to the video resource that we want to play
            //[NOTE]
            //1. rename xxx.ts to xxx.mp4, otherwise cannot find the video file from android.resource://
            //2. from /data/local/tmp/xxx.ts ==>  Needs to set sepolicy as permissive: setenforce 0
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
                            boolean result = mCodecWrapper.writeSample(mExtractor, false, mExtractor.getSampleTime(), mExtractor.getSampleFlags());

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

                        String strDbg = String.format(" presentationTimeUs=0x%x (%x), totalTime=0x%x, , deltaTime=0x%x",
                                                    out_bufferInfo.presentationTimeUs, (out_bufferInfo.presentationTimeUs / 1000), totalTime, deltaTime);
                        Log.i(TAG, " startPlayback(): "  + strDbg);

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

            if (mTunerCodec != null) {
                mTunerCodec.close();
                mTunerCodec = null;
            }

            if ((mTunerCodec = new TunerCodec()) == null) {
                throw new NullPointerException("Create TunerCodec failed!!");
            }

            //open tuner
            mTunerCodec.openTuner();
            //start tune
            mTunerCodec.startTune();

            //open video codec and filter
            mTunerCodec.openVideo();
            //start video codec and filter
            mTunerCodec.startVideo();

            //set play state
            mTunerCodec.setState(STATE_PLAY);
        }

        public void onReadProgramConfig() {
            Log.i(TAG, "onReadProgramConfig()");
            if (mTunerCodec != null) {
                mTunerCodec.mTvProgramConfig.parse();
            }
            mTunerCodec = new TunerCodec();
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

    public void readProgramInfo(TextureView view) {
        mVideoView = view;
        mContext = view.getContext();
        mSurface = new Surface(mVideoView.getSurfaceTexture());
        mHandler.sendEmptyMessage(MSG_READ_PROGRAM_INFO);
    }
}
