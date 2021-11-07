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

import android.os.StatFs
import com.kwai.koom.base.MonitorBuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

internal object OOMFileManager {
    private const val TIME_FORMAT = "yyyy-MM-dd_HH-mm-ss_SSS"

    private lateinit var mRootDirInvoker: (String) -> File//mRootDirInvoker表达式
    private lateinit var mPrefix: String    //前缀是个version

    private lateinit var mRootPath: String  //root路径

    val rootDir by lazy {
        if (this::mRootDirInvoker.isInitialized)//已初始化
            mRootDirInvoker("oom")
        else
            File(mRootPath)
    }

    @JvmStatic
    val hprofAnalysisDir by lazy { File(rootDir, "memory/hprof-aly").apply { mkdirs() } }

    @JvmStatic
    val manualDumpDir by lazy { File(rootDir, "memory/hprof-man").apply { mkdirs() } }

    @JvmStatic
    val threadDumpDir by lazy { File(hprofAnalysisDir, "thread").apply { mkdirs() } }

    @JvmStatic
    val fdDumpDir by lazy { File(hprofAnalysisDir, "fd").apply { mkdirs() } }

    @JvmStatic
    fun init(rootDirInvoker: (String) -> File) {//有两个init，如果有mRootDirInvoker，则以mRootDirInvoker为主
        mRootDirInvoker = rootDirInvoker
        mPrefix = "${MonitorBuildConfig.VERSION_NAME}_"
    }

    @JvmStatic
    fun init(rootPath: String?) {//有两个init，如果没有有mRootDirInvoker，以mRootPath为主
        if (rootPath != null) {
            mRootPath = rootPath
        }
        mPrefix = "${MonitorBuildConfig.VERSION_NAME}_"
    }

    @JvmStatic
    fun createHprofAnalysisFile(date: Date): File {
        val time = SimpleDateFormat(TIME_FORMAT, Locale.CHINESE).format(date)
        return File(hprofAnalysisDir, "$mPrefix$time.hprof").also {
            hprofAnalysisDir.mkdirs()
        }
    }

    @JvmStatic
    fun createJsonAnalysisFile(date: Date): File {
        val time = SimpleDateFormat(TIME_FORMAT, Locale.CHINESE).format(date)
        return File(hprofAnalysisDir, "$mPrefix$time.json").also {
            hprofAnalysisDir.mkdirs()
        }
    }

    @JvmStatic
    fun createHprofOOMDumpFile(date: Date): File {
        val time = SimpleDateFormat(TIME_FORMAT, Locale.CHINESE).format(date)
        return File(manualDumpDir, "$mPrefix$time.hprof").also {
            manualDumpDir.mkdirs()
        }
    }

    @JvmStatic
    fun createDumpFile(dumpDir: File): File {
        return File(dumpDir, "dump.txt").also {
            dumpDir.mkdirs()
        }
    }

    @JvmStatic
    fun isSpaceEnough(): Boolean {
        //https://blog.csdn.net/suyimin2010/article/details/86680731
        //getPath() 方法跟创建 File 对象时传入的路径参数有关，返回构造时传入的路径
        //getAbsolutePath() 方法返回文件的绝对路径，如果构造的时候是全路径就直接返回全路径，如果构造时是相对路径，就返回当前目录的路径 + 构造 File 对象时的路径
        //getCanonicalPath() 方法返回绝对路径，会把 ..\ 、.\ 这样的符号解析掉
        val statFs = StatFs(hprofAnalysisDir.canonicalPath)
        val blockSize = statFs.blockSizeLong//文件系统上块的大小（以字节为单位）
        val availableBlocks = statFs.availableBlocks.toLong()

        return blockSize * availableBlocks > 1.2 * 1024 * 1024//todo 1.2G ? 1.2M 1.2M太少了吧
    }
}