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

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

internal val mainHandler = Handler(Looper.getMainLooper())

//异步
fun async(delayMills: Long = 0L, block: () -> Unit) {
    if (delayMills != 0L) {
        mainHandler.postDelayed({
          MonitorManager.commonConfig.executorServiceInvoker?.invoke()?.submit(block)
                  ?: thread { block() }
        }, delayMills)
    } else {
      //调用线程池运行block，如果为空则创建thread创建block
        MonitorManager.commonConfig.executorServiceInvoker?.invoke()?.submit(block)
                ?: thread { block() }
    }
}

fun postOnMainThread(delayMills: Long = 0L, block: () -> Unit) {
    mainHandler.postDelayed(block, delayMills)
}

fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        mainHandler.post(block)
    }
}

fun postOnMainThread(delay: Long = 0L, runnable: Runnable) {
    mainHandler.postDelayed(runnable, delay)
}

fun removeCallbacks(runnable: Runnable) {
    mainHandler.removeCallbacks(runnable)
}
