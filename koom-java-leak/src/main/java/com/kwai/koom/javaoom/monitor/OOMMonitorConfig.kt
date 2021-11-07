/**
 * Copyright 2020 Kwai, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.javaoom.monitor

import android.os.Build
import com.kwai.koom.base.MonitorBuildConfig
import com.kwai.koom.base.MonitorConfig
import com.kwai.koom.javaoom.monitor.utils.SizeUnit

class OOMMonitorConfig(
        val analysisMaxTimesPerVersion: Int,  //每个版本最多分析次数
        val analysisPeriodPerVersion: Int,    //每个版本分析时间间隔

        val heapThreshold: Float,         //heap阈值
        val fdThreshold: Int,             //fd阈值
        val threadThreshold: Int,         //线程阈值
        val deviceMemoryThreshold: Float, //device memory阈值 还剩多少容量？ todo
        val maxOverThresholdCount: Int,   //超过最大次数阈值 3次 todo
        val forceDumpJavaHeapMaxThreshold: Float,//强制dump java heap最大阈值 0.9大于等于heapThreshold todo
        val forceDumpJavaHeapDeltaThreshold: Int,//强制dump java heap delta阈值 Delta 三角洲 意思是增量 todo

        val loopInterval: Long, //轮询间隔

        val enableHprofDumpAnalysis: Boolean,//是否开启hprofdump分析

        val hprofUploader: OOMHprofUploader?,//hprof上传
        val reportUploader: OOMReportUploader?//日志上传
) : MonitorConfig<OOMMonitor>() {

    class Builder : MonitorConfig.Builder<OOMMonitorConfig> {
        companion object {
            private val DEFAULT_HEAP_THRESHOLD by lazy {
                val maxMem = SizeUnit.BYTE.toMB(Runtime.getRuntime().maxMemory())
                when {
                    maxMem >= 512 - 10 -> 0.8f
                    maxMem >= 256 - 10 -> 0.85f
                    else -> 0.9f
                }
            }

            private val DEFAULT_THREAD_THRESHOLD by lazy {
                if (MonitorBuildConfig.ROM == "EMUI" && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                    450
                } else {
                    750
                }
            }
        }

        private var mAnalysisMaxTimesPerVersion = 5                         //每个版本最多分析5次
        private var mAnalysisPeriodPerVersion = 15 * 24 * 60 * 60 * 1000    //每次分析时间间隔15天

        private var mHeapThreshold: Float? = null
        private var mVssSizeThreshold = 3_650_000 //Only for 32 bit cpu devices. 360M todo 有用到吗
        private var mFdThreshold = 1000             //fd阈值1000
        private var mThreadThreshold: Int? = null   //线程阈值
        private var mDeviceMemoryThreshold: Float = 0.05f//todo
        private var mForceDumpJavaHeapMaxThreshold = 0.90f//达到javaheap的90%的时候强制dump todo
        private var mForceDumpJavaHeapDeltaThreshold = 350_000 //java heap rise 350m in a very short time.短时间增量350M的时候
        private var mMaxOverThresholdCount = 3//超过3次
        private var mLoopInterval = 15_000L //15s轮询一次

        private var mEnableHprofDumpAnalysis = true //enable hprof analysis

        private var mHprofUploader: OOMHprofUploader? = null
        private var mReportUploader: OOMReportUploader? = null

        fun setAnalysisMaxTimesPerVersion(analysisMaxTimesPerVersion: Int) = apply {
            mAnalysisMaxTimesPerVersion = analysisMaxTimesPerVersion
        }

        fun setAnalysisPeriodPerVersion(analysisPeriodPerVersion: Int) = apply {
            mAnalysisPeriodPerVersion = analysisPeriodPerVersion
        }

        /**
         * @param heapThreshold: 堆内存的使用比例[0.0, 1.0]
         */
        fun setHeapThreshold(heapThreshold: Float) = apply {
            mHeapThreshold = heapThreshold
        }

        /**
         * @param vssSizeThreshold: 单位是kb
         */
        fun setVssSizeThreshold(vssSizeThreshold: Int) = apply {
            mVssSizeThreshold = vssSizeThreshold
        }

        fun setFdThreshold(fdThreshold: Int) = apply {
            mFdThreshold = fdThreshold
        }

        fun setThreadThreshold(threadThreshold: Int) = apply {
            mThreadThreshold = threadThreshold
        }

        fun setMaxOverThresholdCount(maxOverThresholdCount: Int) = apply {
            mMaxOverThresholdCount = maxOverThresholdCount
        }

        fun setLoopInterval(loopInterval: Long) = apply {
            mLoopInterval = loopInterval
        }

        fun setEnableHprofDumpAnalysis(enableHprofDumpAnalysis: Boolean) = apply {
            mEnableHprofDumpAnalysis = enableHprofDumpAnalysis
        }

        fun setDeviceMemoryThreshold(deviceMemoryThreshold: Float) = apply {
            mDeviceMemoryThreshold = deviceMemoryThreshold
        }

        fun setForceDumpJavaHeapDeltaThreshold(forceDumpJavaHeapDeltaThreshold: Int) = apply {
            mForceDumpJavaHeapDeltaThreshold = forceDumpJavaHeapDeltaThreshold
        }

        fun setForceDumpJavaHeapMaxThreshold(forceDumpJavaHeapMaxThreshold: Float) = apply {
            mForceDumpJavaHeapMaxThreshold = forceDumpJavaHeapMaxThreshold
        }

        fun setHprofUploader(hprofUploader: OOMHprofUploader) = apply {
            mHprofUploader = hprofUploader
        }

        fun setReportUploader(reportUploader: OOMReportUploader) = apply {
            mReportUploader = reportUploader
        }

        override fun build() = OOMMonitorConfig(
                analysisMaxTimesPerVersion = mAnalysisMaxTimesPerVersion,
                analysisPeriodPerVersion = mAnalysisPeriodPerVersion,

                heapThreshold = mHeapThreshold ?: DEFAULT_HEAP_THRESHOLD,
                fdThreshold = mFdThreshold,
                threadThreshold = mThreadThreshold ?: DEFAULT_THREAD_THRESHOLD,
                deviceMemoryThreshold = mDeviceMemoryThreshold,
                maxOverThresholdCount = mMaxOverThresholdCount,
                loopInterval = mLoopInterval,
                enableHprofDumpAnalysis = mEnableHprofDumpAnalysis,

                forceDumpJavaHeapMaxThreshold = mForceDumpJavaHeapMaxThreshold,
                forceDumpJavaHeapDeltaThreshold = mForceDumpJavaHeapDeltaThreshold,

                hprofUploader = mHprofUploader,
                reportUploader = mReportUploader
        )
    }
}