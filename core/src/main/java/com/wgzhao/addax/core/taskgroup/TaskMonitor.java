/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.wgzhao.addax.core.taskgroup;

import com.wgzhao.addax.common.exception.CommonErrorCode;
import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.core.meta.State;
import com.wgzhao.addax.core.statistics.communication.Communication;
import com.wgzhao.addax.core.statistics.communication.CommunicationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by liqiang on 15/7/23.
 */
public class TaskMonitor
{

    private static final Logger LOG = LoggerFactory.getLogger(TaskMonitor.class);
    private static final TaskMonitor instance = new TaskMonitor();

    private final ConcurrentHashMap<Integer, TaskCommunication> tasks = new ConcurrentHashMap<>();

    private TaskMonitor()
    {
    }

    public static TaskMonitor getInstance()
    {
        return instance;
    }

    public void registerTask(Integer taskid, Communication communication)
    {
        //如果task已经finish，直接返回
        if (communication.isFinished()) {
            return;
        }
        tasks.putIfAbsent(taskid, new TaskCommunication(taskid, communication));
    }

    public void removeTask(Integer taskid)
    {
        tasks.remove(taskid);
    }

    public void report(Integer taskid, Communication communication)
    {
        //如果task已经finish，直接返回
        if (communication.isFinished()) {
            return;
        }
        if (!tasks.containsKey(taskid)) {
            LOG.warn("unexpected: taskid({}) missed.", taskid);
            tasks.putIfAbsent(taskid, new TaskCommunication(taskid, communication));
        }
        else {
            tasks.get(taskid).report(communication);
        }
    }

    public TaskCommunication getTaskCommunication(Integer taskid)
    {
        return tasks.get(taskid);
    }

    public static class TaskCommunication
    {
        private final Integer taskid;
        //记录最后更新的communication
        private long lastAllReadRecords;
        //只有第一次，或者统计变更时才会更新TS
        private long lastUpdateComunicationTS;
        private long ttl;

        private TaskCommunication(Integer taskid, Communication communication)
        {
            this.taskid = taskid;
            lastAllReadRecords = CommunicationTool.getTotalReadRecords(communication);
            ttl = System.currentTimeMillis();
            lastUpdateComunicationTS = ttl;
        }

        public void report(Communication communication)
        {

            ttl = System.currentTimeMillis();
            //采集的数量增长，则变更当前记录, 优先判断这个条件，因为目的是不卡住，而不是expired
            if (CommunicationTool.getTotalReadRecords(communication) > lastAllReadRecords) {
                lastAllReadRecords = CommunicationTool.getTotalReadRecords(communication);
                lastUpdateComunicationTS = ttl;
            }
            else if (isExpired(lastUpdateComunicationTS)) {
                communication.setState(State.FAILED);
                communication.setTimestamp(ttl);
                communication.setThrowable(AddaxException.asAddaxException(CommonErrorCode.TASK_HUNG_EXPIRED,
                        String.format("task(%s) hung expired [allReadRecord(%s), elased(%s)]",
                                taskid, lastAllReadRecords, (ttl - lastUpdateComunicationTS))));
            }
        }

        private boolean isExpired(long lastUpdateComunicationTS)
        {
            //48 hours
            long expiredTime = 172800 * 1000L;
            return System.currentTimeMillis() - lastUpdateComunicationTS > expiredTime;
        }

        public Integer getTaskid()
        {
            return taskid;
        }

        public long getLastAllReadRecords()
        {
            return lastAllReadRecords;
        }

        public long getLastUpdateComunicationTS()
        {
            return lastUpdateComunicationTS;
        }

        public long getTtl()
        {
            return ttl;
        }
    }
}
