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

package com.kwai.koom.javaoom.monitor.tracker.model

import android.os.Build
import android.text.TextUtils
import com.kwai.koom.base.MonitorLog
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

internal object SystemInfo {
    private const val TAG = "OOMMonitor_SystemInfo"
    //https://my.oschina.net/u/4592355/blog/5004330
    //VSS - Virtual Set Size （用处不大）虚拟耗用内存（包含共享库占用的全部内存，以及分配但未使用内存）。
    // 其大小还包括了可能不在RAM中的内存（比如虽然malloc分配了空间，但尚未写入）。
    // VSS 很少被用于判断一个进程的真实内存使用量。
    //RSS - Resident Set Size （用处不大）
    //实际使用物理内存（包含共享库占用的全部内存）。但是RSS还是可能会造成误导，因为它仅仅表示该进程所使用的所有共享库的大小，
    // 它不管有多少个进程使用该共享库，该共享库仅被加载到内存一次。所以RSS并不能准确反映单进程的内存占用情况。
    //PSS - Proportional Set Size （仅供参考）
    //实际使用的物理内存（比例分配共享库占用的内存，按照进程数等比例划分）。
    //例如：如果有三个进程都使用了一个共享库，共占用了30页内存。那么PSS将认为每个进程分别占用该共享库10页的大小。
    //PSS是非常有用的数据，因为系统中所有进程的PSS都相加的话，就刚好反映了系统中的 总共占用的内存。
    // 而当一个进程被销毁之后， 其占用的共享库那部分比例的PSS，将会再次按比例分配给余下使用该库的进程。
    //这样PSS可能会造成一点的误导，因为当一个进程被销毁后， PSS不能准确地表示返回给全局系统的内存。
    //USS - Unique Set Size （非常有用）
    //进程独自占用的物理内存（不包含共享库占用的内存）。USS是非常非常有用的数据，因为它反映了运行一个特定进程真实的边际成本
    // （增量成本）。当一个进程被销毁后，USS是真实返回给系统的内存。当进程中存在一个可疑的内存泄露时，USS是最佳观察数据。
    private val VSS_REGEX = "VmSize:\\s*(\\d+)\\s*kB".toRegex()
    private val RSS_REGEX = "VmRSS:\\s*(\\d+)\\s*kB".toRegex()
    private val THREADS_REGEX = "Threads:\\s*(\\d+)\\s*".toRegex()
    private val MEM_TOTAL_REGEX = "MemTotal:\\s*(\\d+)\\s*kB".toRegex()
    private val MEM_FREE_REGEX = "MemFree:\\s*(\\d+)\\s*kB".toRegex()
    private val MEM_AVA_REGEX = "MemAvailable:\\s*(\\d+)\\s*kB".toRegex()
    //https://www.jianshu.com/p/9edfe9d5eb34
    //ion disp：display 相关的ion模块内存占用 ion是离子 todo
    //cma usage:cma模块占用
    private val MEM_CMA_REGEX = "CmaTotal:\\s*(\\d+)\\s*kB".toRegex()
    private val MEM_ION_REGEX = "ION_heap:\\s*(\\d+)\\s*kB".toRegex()

    var procStatus = ProcStatus()
    var lastProcStatus = ProcStatus()

    var memInfo = MemInfo()
    var lastMemInfo = MemInfo()

    var javaHeap = JavaHeap()
    var lastJavaHeap = JavaHeap()

    //selinux权限问题，先注释掉
    //var dmaZoneInfo: ZoneInfo = ZoneInfo()
    //var normalZoneInfo: ZoneInfo = ZoneInfo()

    fun refresh() {
        lastJavaHeap = javaHeap
        lastMemInfo = memInfo
        lastProcStatus = procStatus

        javaHeap = JavaHeap()
        procStatus = ProcStatus()
        memInfo = MemInfo()

        javaHeap.max = Runtime.getRuntime().maxMemory()
        javaHeap.total = Runtime.getRuntime().totalMemory()
        javaHeap.free = Runtime.getRuntime().freeMemory()
        javaHeap.used = javaHeap.total - javaHeap.free
        javaHeap.rate = 1.0f * javaHeap.used / javaHeap.max

        //读取"/proc/self/status"文件获取procStatus
        File("/proc/self/status").forEachLineQuietly { line ->
            if (procStatus.vssInKb != 0 && procStatus.rssInKb != 0
                    && procStatus.thread != 0) return@forEachLineQuietly

            when {
                line.startsWith("VmSize") -> {
                    procStatus.vssInKb = VSS_REGEX.matchValue(line)
                }

                line.startsWith("VmRSS") -> {
                    procStatus.rssInKb = RSS_REGEX.matchValue(line)
                }

                line.startsWith("Threads") -> {
                    procStatus.thread = THREADS_REGEX.matchValue(line)
                }
            }
        }

        //读取"/proc/meminfo"文件获取memInfo
        File("/proc/meminfo").forEachLineQuietly { line ->
            when {
                line.startsWith("MemTotal") -> {
                    memInfo.totalInKb = MEM_TOTAL_REGEX.matchValue(line)
                }

                line.startsWith("MemFree") -> {
                    memInfo.freeInKb = MEM_FREE_REGEX.matchValue(line)
                }

                line.startsWith("MemAvailable") -> {
                    memInfo.availableInKb = MEM_AVA_REGEX.matchValue(line)
                }

                line.startsWith("CmaTotal") -> {
                    memInfo.cmaTotal = MEM_CMA_REGEX.matchValue(line)
                }

                line.startsWith("ION_heap") -> {
                    memInfo.IONHeap = MEM_ION_REGEX.matchValue(line)
                }
            }
        }

        memInfo.rate = 1.0f * memInfo.availableInKb / memInfo.totalInKb

        MonitorLog.i(TAG, "----OOM Monitor Memory----")
        MonitorLog.i(TAG, "[java] max:${javaHeap.max} used ratio:${(javaHeap.rate * 100).toInt()}%")
        MonitorLog.i(TAG, "[proc] VmSize:${procStatus.vssInKb}kB VmRss:${procStatus.rssInKb}kB " + "Threads:${procStatus.thread}")
        MonitorLog.i(TAG, "[meminfo] MemTotal:${memInfo.totalInKb}kB MemFree:${memInfo.freeInKb}kB " + "MemAvailable:${memInfo.availableInKb}kB")
        MonitorLog.i(TAG, "avaliable ratio:${(memInfo.rate * 100).toInt()}% CmaTotal:${memInfo.cmaTotal}kB ION_heap:${memInfo.IONHeap}kB")
    }

    data class ProcStatus(var thread: Int = 0, var vssInKb: Int = 0, var rssInKb: Int = 0)

    data class MemInfo(var totalInKb: Int = 0, var freeInKb: Int = 0, var availableInKb: Int = 0,
                       var IONHeap: Int = 0, var cmaTotal: Int = 0, var rate: Float = 0f)

    data class JavaHeap(var max: Long = 0, var total: Long = 0, var free: Long = 0,
                        var used: Long = 0, var rate: Float = 0f)

    //正则表达式
    private fun Regex.matchValue(s: String) = matchEntire(s.trim())
            ?.groupValues?.getOrNull(1)?.toInt() ?: 0

    //读取文件的每一行
    private fun File.forEachLineQuietly(charset: Charset = Charsets.UTF_8, action: (line: String) -> Unit) {
        kotlin.runCatching {
            // Note: close is called at forEachLineQuietly
            BufferedReader(InputStreamReader(FileInputStream(this), charset)).forEachLine(action)
        }.onFailure { exception -> exception.printStackTrace() }
    }

    /**
     * 设备是否支持arm64
     */
    fun isSupportArm64(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        return supportedAbis().contains("arm64-v8a")
    }

    fun supportedAbis(): Array<String?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && Build.SUPPORTED_ABIS.isNotEmpty()) {
            Build.SUPPORTED_ABIS
        } else if (!TextUtils.isEmpty(Build.CPU_ABI2)) {//todo
            arrayOf(Build.CPU_ABI, Build.CPU_ABI2)
        } else {
            arrayOf(Build.CPU_ABI)
        }
    }
}