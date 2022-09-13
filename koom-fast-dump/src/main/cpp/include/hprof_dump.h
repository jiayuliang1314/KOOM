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
 * Created by Qiushi Xue <xueqiushi@kuaishou.com> on 2021.
 *
 */

#ifndef KOOM_HPROF_DUMP_H
#define KOOM_HPROF_DUMP_H

#include <android-base/macros.h>

#include <memory>
#include <string>

namespace kwai {
    namespace leak_monitor {

        // todo What caused the GC? gc原因
        enum GcCause {
            // Invalid GC cause used as a placeholder.用作占位符的GC原因无效。
            kGcCauseNone,
            // GC triggered by a failed allocation. Thread doing allocation is blocked
            // waiting for GC before
            // retrying allocation.
            //由失败的分配触发的GC。执行分配的线程在重试分配之前被阻止，等待GC。
            kGcCauseForAlloc,
            // A background GC trying to ensure there is free memory ahead of allocations.
            //后台GC试图确保在分配之前有可用内存。
            kGcCauseBackground,
            // An explicit System.gc() call.
            //调用System.gc()导致的gc
            kGcCauseExplicit,
            // GC triggered for a native allocation when NativeAllocationGcWatermark is
            // exceeded.
            // (This may be a blocking GC depending on whether we run a non-concurrent
            // collector).
            //当超过NativeAllocationGcWatermark时，为本机分配触发GC。
            // （这可能是阻塞GC，具体取决于我们是否运行非并发收集器）。
            kGcCauseForNativeAlloc,
            // GC triggered for a collector transition.
            //为收集器转换触发GC。
            kGcCauseCollectorTransition,
            // Not a real GC cause, used when we disable moving GC (currently for
            // GetPrimitiveArrayCritical).
            //不是真正的GC原因，在禁用移动GC时使用（当前用于GetPrimitiveArrayCritical）。
            kGcCauseDisableMovingGc,
            // Not a real GC cause, used when we trim the heap.
            //不是真正的GC原因，在修剪堆时使用。
            kGcCauseTrim,
            // Not a real GC cause, used to implement exclusion between GC and
            // instrumentation.
            //不是真正的GC原因，用于实现GC和仪器仪表。
            kGcCauseInstrumentation,
            // Not a real GC cause, used to add or remove app image spaces.
            //不是真正的GC原因，用于添加或删除应用程序图像空间。
            kGcCauseAddRemoveAppImageSpace,
            // Not a real GC cause, used to implement exclusion between GC and debugger.
            //不是真正的GC原因，用于在GC和调试器之间实现排除
            kGcCauseDebugger,
            // GC triggered for background transition when both foreground and background
            // collector are CMS.
            //当前台和后台采集器均为CMS时，为后台转换触发GC。
            kGcCauseHomogeneousSpaceCompact,
            // Class linker cause, used to guard filling art methods with special values.
            //类链接器原因，用于保护使用特殊值填充艺术方法。
            kGcCauseClassLinker,
            // Not a real GC cause, used to implement exclusion between code cache
            // metadata and GC.
            //不是真正的GC原因，用于实现代码缓存元数据和GC之间的排除
            kGcCauseJitCodeCache,
            // Not a real GC cause, used to add or remove system-weak holders.
            //不是真正的GC原因，用于添加或删除系统弱保持器。
            kGcCauseAddRemoveSystemWeakHolder,
            // Not a real GC cause, used to prevent hprof running in the middle of GC.
            //不是一个真正的GC原因，用于防止HPROF在GC的中间运行
            kGcCauseHprof,
            // Not a real GC cause, used to prevent GetObjectsAllocated running in the
            // middle of GC.
            //不是真正的GC原因，用于防止GetObjectsLocated在GC的中间部分。
            kGcCauseGetObjectsAllocated,
            // GC cause for the profile saver.
            //配置文件保护程序的GC原因。
            kGcCauseProfileSaver,
            // GC cause for running an empty checkpoint.
            //GC导致运行空检查点。
            kGcCauseRunEmptyCheckpoint,
        };

        // 可以执行哪些类型的集合。Which types of collections are able to be performed.
        enum CollectorType {
            // No collector selected.
            //无类别collector
            kCollectorTypeNone,
            //非并发标记清除。
            // Non concurrent mark-sweep.
            kCollectorTypeMS,
            //并发标记清除。
            // Concurrent mark-sweep.
            kCollectorTypeCMS,
            //半空间/标记-清除混合，可实现压缩。
            // Semi-space / mark-sweep hybrid, enables compaction.
            kCollectorTypeSS,
            //堆修剪收集器，不做任何实际的收集。
            // Heap trimming collector, doesn't do any actual collecting.
            kCollectorTypeHeapTrim,
            //一个（主要是）并发复制收集器
            // A (mostly) concurrent copying collector.
            kCollectorTypeCC,
            //并发复制收集器的后台压缩。
            // The background compaction of the concurrent copying collector.
            kCollectorTypeCCBackground,
            //仪表临界区假收集器。
            // Instrumentation critical section fake collector.
            kCollectorTypeInstrumentation,
            //用于添加或删除应用程序图像空间的假收集器。
            // Fake collector for adding or removing application image spaces.
            kCollectorTypeAddRemoveAppImageSpace,
            //用于在 GC 和调试器之间实现排除的假收集器。
            // Fake collector used to implement exclusion between GC and debugger.
            kCollectorTypeDebugger,
            // A homogeneous space compaction collector used in background transition
            // when both foreground and background collector are CMS.
            //当前景和背景收集器都是 CMS 时，用于背景转换的同构空间压缩收集器。
            kCollectorTypeHomogeneousSpaceCompact,
            // Class linker fake collector.
            ///类链接器假收集器。
            kCollectorTypeClassLinker,
            // JIT Code cache fake collector.
            //JIT 代码缓存假收集器。
            kCollectorTypeJitCodeCache,
            // Hprof fake collector.
            //Hprof 假收藏家。
            kCollectorTypeHprof,
            // Fake collector for installing/removing a system-weak holder.
            //用于安装/移除系统弱支架的假收集器。
            kCollectorTypeAddRemoveSystemWeakHolder,
            // Fake collector type for GetObjectsAllocated
            //Get Object Allocated 的假收集器类型
            kCollectorTypeGetObjectsAllocated,
            // Fake collector type for ScopedGCCriticalSection
            //ScopedGCCriticalSection 的假收集器类型
            kCollectorTypeCriticalSection,
        };

        class HprofDump {
        public:
            //获取HprofDump实例
            static HprofDump &GetInstance();

            //初始化
            void Initialize();

            //SuspendAndFork
            pid_t SuspendAndFork();

            //ResumeAndWait
            bool ResumeAndWait(pid_t pid);

        private:
            HprofDump();

            ~HprofDump() = default;

            //https://blog.csdn.net/u011157036/article/details/45247965
            //有时候，进行类体设计时，会发现某个类的对象是独一无二的，没有完全相同的对象，也就是对该类对象做副本没有任何意义．
            //因此，需要限制编译器自动生动的拷贝构造函数和赋值构造函数．一般参用下面的宏定义的方式进行限制，代码如下：
            DISALLOW_COPY_AND_ASSIGN(HprofDump);
            //初始化完成
            bool init_done_;
            //api版本
            int android_api_;

            //todo 作用域挂起所有实例的占位符
            // ScopedSuspendAll instance placeholder 作用域挂起所有实例的占位符
            std::unique_ptr<char[]> ssa_instance_;
            //todo 作用域GC临界段
            // ScopedGCCriticalSection instance placeholder
            std::unique_ptr<char[]> sgc_instance_;

            /**
             * Function pointer for ART <= Android Q
             * 方法指针 ART小于等于Android q的
             */
             //suspend vm的方法
            // art::Dbg::SuspendVM
            void (*suspend_vm_fnc_)();

            //resume vm的方法
            // art::Dbg::ResumeVM
            void (*resume_vm_fnc_)();

            /**
             * Function pointer for ART Android R
             * todo 方法指针 art android R的
             */
             //ScopedSuspendAll构造函数
            // art::ScopedSuspendAll::ScopedSuspendAll()
            void (*ssa_constructor_fnc_)(void *handle, const char *cause,
                                         bool long_suspend);

            //ScopedSuspendAll析构函数
            // todo art::ScopedSuspendAll::~ScopedSuspendAll()
            void (*ssa_destructor_fnc_)(void *handle);

            //todo ScopedGCCriticalSection 构造函数 Critical Section临界截面
            // art::gc::ScopedGCCriticalSection::ScopedGCCriticalSection()
            void (*sgc_constructor_fnc_)(void *handle, void *self, GcCause cause,
                                         CollectorType collector_type);

            //todo ScopedGCCriticalSection析构函数，Scoped作用域，范围，Critical Section临界截面，临界区;临界段;临界区域;关键部分;关键区段
            // art::gc::ScopedGCCriticalSection::~ScopedGCCriticalSection()
            void (*sgc_destructor_fnc_)(void *handle);

            //todo 指针 mutator突变体
            // art::Locks::mutator_lock_
            void **mutator_lock_ptr_;

            //专有的锁方法
            //Mutex 互斥
            //todo art::ReaderWriterMutex::ExclusiveLock
            void (*exclusive_lock_fnc_)(void *, void *self);

            //专有的解锁方法
            //Mutex 互斥
            //todo art::ReaderWriterMutex::ExclusiveUnlock
            void (*exclusive_unlock_fnc_)(void *, void *self);
        };

    }  // namespace leak_monitor
}  // namespace kwai
#endif  // KOOM_HPROF_DUMP_H
