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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.json.JSONObject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

object MonitorManager {
    internal val MONITOR_MAP = ConcurrentHashMap<Class<*>, Monitor<*>>()

    internal lateinit var commonConfig: CommonConfig

    @JvmStatic
    fun initCommonConfig(commonConfig: CommonConfig) = apply {
        this.commonConfig = commonConfig
    }

    //todo
    @JvmStatic
    fun <M : MonitorConfig<*>> addMonitorConfig(config: M) = apply {
        var supperType: Type? = config.javaClass.genericSuperclass
        while (supperType is Class<*>) {
            supperType = supperType.genericSuperclass//`getGenericSuperclass` 会包含该超类的泛型。
        }
        //ParameterizedType参数化类型
        if (supperType !is ParameterizedType) {
            throw java.lang.RuntimeException("config must be parameterized")
        }

        //取出指定的泛型。
        val monitorType = supperType.actualTypeArguments[0] as Class<Monitor<M>>

        if (MONITOR_MAP.containsKey(monitorType)) {
            return@apply
        }

        val monitor = try {
            monitorType.getDeclaredField("INSTANCE").get(null) as Monitor<M>
        } catch (e: Throwable) {
            monitorType.newInstance() as Monitor<M>
        }

        MONITOR_MAP[monitorType] = monitor

        monitor.init(commonConfig, config)

        monitor.logMonitorEvent()
    }

    @JvmStatic
    fun getApplication() = commonConfig.application

    @Deprecated("Use Monitor Directly")
    @JvmStatic
    fun <M : Monitor<*>> getMonitor(clazz: Class<M>): M {
        return MONITOR_MAP[clazz] as M
    }

    @Deprecated("Use Monitor#isInitialized Directly")
    @JvmStatic
    fun <M : Monitor<*>> isInitialized(clazz: Class<M>): Boolean {
        return MONITOR_MAP[clazz] != null
    }

    @JvmStatic
    fun onApplicationCreate() {
        registerApplicationExtension()

        registerMonitorEventObserver()
    }

    //onStateChanged ON_START的时候打印MONITOR_MAP的信息
    private fun registerMonitorEventObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
          private var mHasLogMonitorEvent = false

          override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_START) {
              logAllMonitorEvent()
            }
          }

          private fun logAllMonitorEvent() {
            if (mHasLogMonitorEvent) return
            mHasLogMonitorEvent = true

            mutableMapOf<Any?, Any?>()
                    .apply { MONITOR_MAP.forEach { putAll(it.value.getLogParams()) } }
                    //also函数的结构实际上和let很像唯一的区别就是返回值的不一样，let是以闭包的形式返回，
                    // 返回函数体内最后一行的值，如果最后一行为空就返回一个Unit类型的默认值。
                    // 而also函数返回的则是传入对象的本身
                    .also {
                      MonitorLogger.addCustomStatEvent("switch-stat", JSONObject(it).toString())
                    }
          }
        })
    }

    private fun <C> Monitor<C>.logMonitorEvent() {
        if (!getApplication().isForeground) return

        mutableMapOf<Any?, Any?>()
                .apply { putAll(this@logMonitorEvent.getLogParams()) }// todo 什么语法
                .also {
                    MonitorLogger.addCustomStatEvent("switch-stat", JSONObject(it).toString())
                }
    }
}