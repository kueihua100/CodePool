/*
 * Copyright 2020 The Android Open Source Project
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

#include "DemuxTests.h"
#include "DescramblerTests.h"
#include "FrontendTests.h"
#include "LnbTests.h"
#include <assert.h>

#define EXPECT_TRUE(a)		if (a != true) assert(0);
#define EXPECT_TRUE_PTR(a)  if (a == nullptr) assert(0);
#define ASSERT_TRUE(a)		if (a != true) assert(0);
#define ASSERT_NE(a, b)		if (a != b) assert(0);

using android::hardware::tv::tuner::V1_0::DataFormat;
using android::hardware::tv::tuner::V1_0::IDescrambler;
typedef bool AssertionResult;

  
static AssertionResult success() {
    return true;
}

namespace android {

void initConfiguration() {
    ALOGD("[%s:%d]", __FUNCTION__, __LINE__);

    initFrontendConfig();
    initFrontendScanConfig();
    initLnbConfig();
    initFilterConfig();
    initTimeFilterConfig();
    initDvrConfig();
    initDescramblerConfig();
}

AssertionResult filterDataOutputTestBase(FilterTests tests) {
    // Data Verify Module
    std::map<uint32_t, sp<FilterCallback>>::iterator it;
    std::map<uint32_t, sp<FilterCallback>> filterCallbacks = tests.getFilterCallbacks();
    for (it = filterCallbacks.begin(); it != filterCallbacks.end(); it++) {
        it->second->testFilterDataOutput();
    }
    return success();
}

class TunerFrontendHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
};

class TunerLnbHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mLnbTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    sp<ITuner> mService;
    LnbTests mLnbTests;
};

class TunerDemuxHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mFilterTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
};

class TunerFilterHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mFilterTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    void configSingleFilterInDemuxTest(FilterConfig filterConf, FrontendConfig frontendConf);
    void testTimeFilter(TimeFilterConfig filterConf);

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
};

class TunerBroadcastHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        ALOGD("[%s:%d]", __FUNCTION__, __LINE__);
        mService = ITuner::getService();
        if (mService == nullptr) {
            ALOGE("[%s:%d] Failed in getService()", __FUNCTION__, __LINE__);
        }

        initConfiguration();
        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mFilterTests.setService(mService);
        mLnbTests.setService(mService);
        mDvrTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
    LnbTests mLnbTests;
    DvrTests mDvrTests;

    AssertionResult filterDataOutputTest(vector<string> goldenOutputFiles);

    void broadcastMultiFilterTest(FilterConfig filterConf, FilterConfig filterConf1, FilterConfig filterConf2, FrontendConfig frontendConf);
    void broadcastSingleFilterTest1(FilterConfig filterConf, FrontendConfig frontendConf);
    void broadcastAVFilterTest1(FilterConfig filterConf1, FilterConfig filterConf2, FrontendConfig frontendConf);
    void broadcastallFilterTest(FilterConfig filterConf, FilterConfig filterConf1, FilterConfig filterConf2,
                                                FilterConfig filterConf3, FilterConfig filterConf4, FrontendConfig frontendConf);


    void broadcastSingleFilterTest(FilterConfig filterConf, FrontendConfig frontendConf);
    void broadcastSingleFilterTestWithLnb(FilterConfig filterConf, FrontendConfig frontendConf,
                                          LnbConfig lnbConf);

  private:
    uint32_t* mLnbId = nullptr;
};

class TunerPlaybackHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mFilterTests.setService(mService);
        mDvrTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
    DvrTests mDvrTests;

    AssertionResult filterDataOutputTest(vector<string> goldenOutputFiles);

    void playbackSingleFilterTest(FilterConfig filterConf, DvrConfig dvrConf);
};

class TunerRecordHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        ASSERT_NE(mService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mFilterTests.setService(mService);
        mDvrTests.setService(mService);
        mLnbTests.setService(mService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    void attachSingleFilterToRecordDvrTest(FilterConfig filterConf, FrontendConfig frontendConf,
                                           DvrConfig dvrConf);
    void recordSingleFilterTest(FilterConfig filterConf, FrontendConfig frontendConf,
                                DvrConfig dvrConf);
    void recordSingleFilterTestWithLnb(FilterConfig filterConf, FrontendConfig frontendConf,
                                       DvrConfig dvrConf, LnbConfig lnbConf);

    sp<ITuner> mService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
    DvrTests mDvrTests;
    LnbTests mLnbTests;

  private:
    uint32_t* mLnbId = nullptr;
};

#if 0
class TunerDescramblerHidlTest /*: public testing::TestWithParam<std::string>*/ {
  public:
     void SetUp()  {
        mService = ITuner::getService();
        mCasService = IMediaCasService::getService();
        ASSERT_NE(mService, nullptr);
        ASSERT_NE(mCasService, nullptr);
        initConfiguration();

        mFrontendTests.setService(mService);
        mDemuxTests.setService(mService);
        mDvrTests.setService(mService);
        mDescramblerTests.setService(mService);
        mDescramblerTests.setCasService(mCasService);
    }

  public:
    static void description(const std::string& description) {
        //RecordProperty("description", description);
    }

    void scrambledBroadcastTest(set<struct FilterConfig> mediaFilterConfs,
                                FrontendConfig frontendConf, DescramblerConfig descConfig);
    AssertionResult filterDataOutputTest(vector<string> /*goldenOutputFiles*/);

    sp<ITuner> mService;
    sp<IMediaCasService> mCasService;
    FrontendTests mFrontendTests;
    DemuxTests mDemuxTests;
    FilterTests mFilterTests;
    DescramblerTests mDescramblerTests;
    DvrTests mDvrTests;
};
#endif

} //android