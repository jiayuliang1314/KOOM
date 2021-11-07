/*
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
 * <p>
 * A jvm hprof dumper which use fork and don't block main process.
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.javaoom.hprof;

import android.os.Build;
import android.os.Debug;

import com.kwai.koom.base.MonitorLog;

import java.io.IOException;

//https://blog.csdn.net/biaozige/article/details/81171637
//art就是虚拟机
//JAVA 代码是怎么执行的？
//为了使代码和平台无关，JAVA开发了 JVM，即 Java 虚拟机。它为每一个平台开发一个 JVM，也就意味着 JVM 是和平台相关的。
//Java 编译器将 .java 文件转换成 .class文件，也就是字节码。最终将字节码提供给 JVM，由 JVM 将它转换成机器码。
//
//这比解释器要快但是比 C++ 编译要慢。
//
//Android 代码是怎么执行的
//在 Android 中，Java 类被转换成 DEX 字节码。DEX 字节码通过 ART 或者 Dalvik runtime 转换成机器码。
//这里 DEX 字节码和设备架构无关。
//
//Dalvik 是一个基于 JIT（Just in time）编译的引擎。使用 Dalvik 存在一些缺点，所以从 Android 4.4（Kitkat）
// 开始引入了 ART 作为运行时，从 Android 5.0（Lollipop）开始 ART 就全面取代了Dalvik。Android 7.0 向 ART
// 中添加了一个 just-in-time（JIT）编译器，这样就可以在应用运行时持续的提高其性能。
public class ForkJvmHeapDumper extends HeapDumper {

    private static final String TAG = "OOMMonitor_ForkJvmHeapDumper";

    public ForkJvmHeapDumper() {
        super();
        if (soLoaded) {
          //step 1
            init();
        }
    }

    @Override
    public boolean dump(String path) {
        MonitorLog.i(TAG, "dump " + path);
        if (!soLoaded) {
            MonitorLog.e(TAG, "dump failed caused by so not loaded!");
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            MonitorLog.e(TAG, "dump failed caused by version not supported!");
            return false;
        }

        boolean dumpRes = false;
        try {
            MonitorLog.i(TAG, "before suspend and fork.");
            //step 2
            int pid = suspendAndFork();//todo pid是啥意思
            if (pid == 0) {
                // Child process
                Debug.dumpHprofData(path);
                exitProcess();//step 3
            } else if (pid > 0) {
                // Parent process
                dumpRes = resumeAndWait(pid);//todo
                MonitorLog.i(TAG, "notify from pid " + pid);
            }
        } catch (IOException e) {
            MonitorLog.e(TAG, "dump failed caused by " + e.toString());
            e.printStackTrace();
        }
        return dumpRes;
    }

    /**
     * Init before do dump. step 1
     */
    private native void init();

    /**
     * Suspend the whole ART, and then fork a process for dumping hprof.
     *
     * @return return value of fork
     */
    private native int suspendAndFork();//todo 返回 //step 2

    /**
     * Resume the whole ART, and then wait child process to notify.
     *
     * @param pid pid of child process.
     */
    private native boolean resumeAndWait(int pid);//todo

    /**
     * Exit current process.
     */
    private native void exitProcess();//step 3
}
