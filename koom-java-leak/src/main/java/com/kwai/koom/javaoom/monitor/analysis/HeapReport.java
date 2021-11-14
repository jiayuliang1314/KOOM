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
 * <p>
 * Heap report file json format.
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.javaoom.monitor.analysis;

import java.util.ArrayList;
import java.util.List;

public class HeapReport {

    public RunningInfo runningInfo = new RunningInfo();
    public List<GCPath> gcPaths = new ArrayList<>();//引用链gc path of suspected objects
    //类及其实例数量Class's instances count list，
    // 这里只关注了activity,fragment，bitmap，Window，NativeAllocationRegistry，CleanerThunk
    //参考HeapAnalysisService
    public List<ClassInfo> classInfos = new ArrayList<>();
    public List<LeakObject> leakObjects = new ArrayList<>();//泄漏的大对象，bitmap，基本数据类型的数组，加入大对象泄露json
    public Boolean analysisDone;//flag to record whether hprof is analyzed already.
    public Integer reAnalysisTimes;//flag to record hprof reanalysis times.

    //device and app running info
    public static class RunningInfo {
        //JVM info
        public String jvmMax;//jvm max memory in MB jvm最大内存
        public String jvmUsed;//jvm used memory in MB jvm已经用过多少了

        //https://my.oschina.net/u/4592355/blog/5004330
        //https://www.cnblogs.com/liyuanhong/articles/7839762.html
        //在linux下表示内存的耗用情况有四种不同的表现形式：
        // VSS - Virtual Set Size 虚拟耗用内存（包含共享库占用的内存）
        // RSS - Resident Set Size 实际使用物理内存（包含共享库占用的内存）
        // PSS - Proportional Set Size 实际使用的物理内存（比例分配共享库占用的内存）
        // USS - Unique Set Size 进程独自占用的物理内存（不包含共享库占用的内存）
        //memory info
        public String vss;//vss memory in MB 进程vss
        public String pss;//pss memory in MB 进程pss
        public String rss;//rss memory in MB 进程rss
        public String threadCount;//线程个数
        public String fdCount;    //fd个数
        public List<String> threadList = new ArrayList<>();//线程信息
        public List<String> fdList = new ArrayList<>();    //fd信息

        //Device info
        public String sdkInt;       //sdk版本
        public String manufacture;  //厂商
        public String buildModel;   //版本

        //App info
        public String appVersion;
        public String currentPage;  //页面
        public String usageSeconds; //耗费时间
        public String nowTime;      //上报时间
        public String deviceMemTotal;     //device内存
        public String deviceMemAvaliable; //device可用内存

        public String dumpReason;//heap dump trigger reason,dump原因
        public String analysisReason;//没有用到，analysis trigger reason，分析原因

        //KOOM Perf data
        public String koomVersion;
        public String filterInstanceTime; //过滤泄漏对象所花的时间
        public String findGCPathTime;     //发现GC引用链时间
    }

    /**
     * GC Path means path of object to GC Root, it can also be called as reference chain.
     */
    public static class GCPath {
        public Integer instanceCount;//引用链上有多少对象 instances number of same path to gc root
        public String leakReason;//reason of why instance is suspected
        public String gcRoot;
        public String signature;//signature are computed by the sha1 of reference chain
        public List<PathItem> path = new ArrayList<>();

        //引用链Item
        public static class PathItem {
            String reference;//referenced instance's classname + filed name
            String referenceType;//todo such as INSTANCE_FIELD/ARRAY_ENTRY/STATIC_FIELD
            String declaredClass;//对于从祖先类继承字段的情况。for cases when filed is inherited from ancestor's class.
        }
    }

    /**
     * ClassInfo contains data which describes the instances number of the Class.
     * ClassInfo 包含描述类的实例数量的数据。
     */
    public static class ClassInfo {
        public String className;
        public String instanceCount;//类的实例数量All instances's count of this class.
        public String leakInstanceCount;//no use 没用到 All leaked instances's count of this class.
    }

    public static class LeakObject {
        public String className;
        public String size;
        public String objectId;
        public String extDetail;//todo
    }
}
