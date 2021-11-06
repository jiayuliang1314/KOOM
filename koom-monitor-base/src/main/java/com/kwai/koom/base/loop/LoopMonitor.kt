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
package com.kwai.koom.base.loop

import android.os.Handler
import com.kwai.koom.base.Monitor
import java.util.concurrent.Callable//可调用

abstract class LoopMonitor<C> : Monitor<C>(), Callable<LoopMonitor.LoopState> {
    companion object {
        private const val DEFAULT_LOOP_INTERVAL = 1000L
    }

    //是否停止了
    //https://www.jianshu.com/p/3963e64e7fe7
    //在kotlin中没有volatile关键字，但是有@Volatile注解，
    @Volatile//@Volatile将把JVM支持字段标记为volatile
    private var mIsLoopStopped = true

    //单独线程
    private val mLoopRunnable = object : Runnable {
        override fun run() {
            //如果call返回Terminate则停止
            if (call() == LoopState.Terminate) {
                return
            }

            if (mIsLoopStopped) {
                return
            }

            getLoopHandler().removeCallbacks(this)
            getLoopHandler().postDelayed(this, getLoopInterval())
        }
    }

    //开启
    open fun startLoop(
            clearQueue: Boolean = true,
            postAtFront: Boolean = false,
            delayMillis: Long = 0L
    ) {
        if (clearQueue) getLoopHandler().removeCallbacks(mLoopRunnable)

        if (postAtFront) {
            getLoopHandler().postAtFrontOfQueue(mLoopRunnable)
        } else {
            getLoopHandler().postDelayed(mLoopRunnable, delayMillis)
        }

        mIsLoopStopped = false
    }

    //停止
    open fun stopLoop() {
        mIsLoopStopped = true

        getLoopHandler().removeCallbacks(mLoopRunnable)
    }

    //1s中
    protected open fun getLoopInterval(): Long {
        return DEFAULT_LOOP_INTERVAL
    }

    //一个开启消息循环线程的Handler
    protected open fun getLoopHandler(): Handler {
        return commonConfig.loopHandlerInvoker()
    }

    sealed class LoopState {
        //两个状态Continue
        object Continue : LoopState()
        //两个状态Terminate
        object Terminate : LoopState()
    }
}