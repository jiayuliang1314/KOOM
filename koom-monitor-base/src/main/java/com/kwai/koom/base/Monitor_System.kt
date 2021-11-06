/*
 * Copyright (c) 2021. Kwai, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author KOOM Team
 *
 */
package com.kwai.koom.base

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.util.regex.Pattern

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

private val VSS_REGEX = "VmSize:\\s*(\\d+)\\s*kB".toRegex()//todo
private val RSS_REGEX = "VmRSS:\\s*(\\d+)\\s*kB".toRegex()//todo
private val THREADS_REGEX = "Threads:\\s*(\\d+)\\s*".toRegex()

private val MEM_TOTAL_REGEX = "MemTotal:\\s*(\\d+)\\s*kB".toRegex()
private val MEM_FREE_REGEX = "MemFree:\\s*(\\d+)\\s*kB".toRegex()
private val MEM_AVA_REGEX = "MemAvailable:\\s*(\\d+)\\s*kB".toRegex()
//https://www.jianshu.com/p/9edfe9d5eb34
//ion disp：display 相关的ion模块内存占用 ion是离子 todo
//cma usage:cma模块占用
private val MEM_CMA_REGEX = "CmaTotal:\\s*(\\d+)\\s*kB".toRegex()//todo
private val MEM_ION_REGEX = "ION_heap:\\s*(\\d+)\\s*kB".toRegex()//todo

private var mCpuCoreCount: Int? = null
private var mRamTotalSize: Long? = null
private var mCpuMaxFreq: Double? = null

@JvmField
var lastProcessStatus = ProcessStatus()

@JvmField
var lastMemInfo = MemInfo()

@JvmField
var lastJavaHeap = JavaHeap()

fun getRamTotalSize(): Long {
    return mRamTotalSize ?: File("/proc/meminfo").useLines {
        it.forEach { line ->
            if (line.contains("MemTotal")) {
                val array = line.split("\\s+".toRegex()).toTypedArray()
                return@useLines array.getOrElse(1) { "0" }.toLong() shl 10
            }
        }
        return@useLines 0L
    }.also { mRamTotalSize = it }
}

fun getRamAvailableSize(context: Context): Long {
    val memoryInfo = ActivityManager.MemoryInfo()

    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .also { it.getMemoryInfo(memoryInfo) }

    return memoryInfo.availMem
}

fun getCpuCoreCount(): Int {
    return mCpuCoreCount
            ?: runCatching {
                File("/sys/devices/system/cpu/")
                        .listFiles { pathname -> Pattern.matches("cpu[0-9]+", pathname.name) }
                        ?.size
                        ?: 0
            }.getOrDefault(Runtime.getRuntime().availableProcessors()).also { mCpuCoreCount = it }
}

fun getCpuMaxFreq(): Double {
    return mCpuMaxFreq
            ?: runCatching {
                (File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                        .readFirstLine()
                        ?.trim()
                        ?.toDouble()
                        ?: 0.0) / 1000
            }.getOrDefault(0.0).also { mCpuMaxFreq = it }
}

/**
 * Get Pss/Vss/etc.
 */
fun getProcessStatus(): ProcessStatus {
    val processStatus = ProcessStatus()
    runCatching {
        File("/proc/self/status").useLines {
            it.forEach { line ->//读取文件的每行
                //如果都不为0的时候返回信息
                if (processStatus.vssKbSize != 0L && processStatus.rssKbSize != 0L
                        && processStatus.threadsCount != 0L) {
                    lastProcessStatus = processStatus
                    return processStatus
                }
                when {
                    line.startsWith("VmSize") -> processStatus.vssKbSize = VSS_REGEX.matchValue(line)
                    line.startsWith("VmRSS") -> processStatus.rssKbSize = RSS_REGEX.matchValue(line)
                    line.startsWith("Threads") ->
                        processStatus.threadsCount = THREADS_REGEX.matchValue(line)
                }
            }
        }
    }
    lastProcessStatus = processStatus
    return processStatus
}

fun getMemoryInfo(): MemInfo {
    val memInfo = MemInfo()
    File("/proc/meminfo").useLines {
        it.forEach { line ->
            when {
                line.startsWith("MemTotal") -> memInfo.totalInKb = MEM_TOTAL_REGEX.matchValue(line)
                line.startsWith("MemFree") -> memInfo.freeInKb = MEM_FREE_REGEX.matchValue(line)
                line.startsWith("MemAvailable") -> memInfo.availableInKb = MEM_AVA_REGEX.matchValue(line)
                line.startsWith("CmaTotal") -> memInfo.cmaTotal = MEM_CMA_REGEX.matchValue(line)
                line.startsWith("ION_heap") -> memInfo.IONHeap = MEM_ION_REGEX.matchValue(line)
            }
        }
    }
    memInfo.rate = 1.0f * memInfo.availableInKb / memInfo.totalInKb
    lastMemInfo = memInfo
    return memInfo
}

fun getJavaHeap(): JavaHeap {
    val javaHeap = JavaHeap()
    javaHeap.max = Runtime.getRuntime().maxMemory()
    javaHeap.total = Runtime.getRuntime().totalMemory()
    javaHeap.free = Runtime.getRuntime().freeMemory()
    javaHeap.used = javaHeap.total - javaHeap.free
    javaHeap.rate = 1.0f * javaHeap.used / javaHeap.max
    lastJavaHeap = javaHeap
    return javaHeap
}

class ProcessStatus {
    @JvmField
    var vssKbSize: Long = 0

    @JvmField
    var rssKbSize: Long = 0

    @JvmField
    var threadsCount: Long = 0
}

class MemInfo(
        @JvmField
        var totalInKb: Long = 0,
        @JvmField
        var freeInKb: Long = 0,
        @JvmField
        var availableInKb: Long = 0,
        @JvmField
        var IONHeap: Long = 0,//todo
        @JvmField
        var cmaTotal: Long = 0,//todo
        @JvmField
        var rate: Float = 0f
)

//https://blog.csdn.net/qijingwang/article/details/86162648
//JvmField在这里是生成get set方法，在伴生对象里，用于消除调用Companion
class JavaHeap(
        @JvmField
        var max: Long = 0,
        @JvmField
        var total: Long = 0,
        @JvmField
        var free: Long = 0,
        @JvmField
        var used: Long = 0,
        @JvmField
        var rate: Float = 0f
)

private fun Regex.matchValue(s: String) = matchEntire(s.trim())
        ?.groupValues?.getOrNull(1)?.toLong() ?: 0L