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

package com.wgzhao.addax.plugin.reader.hbase11xsqlreader;

import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.common.util.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class HbaseSQLReaderConfig
{
    private static final Logger LOG = LoggerFactory.getLogger(HbaseSQLReaderConfig.class);
    private Configuration originalConfig;     // 原始的配置数据

    // 集群配置
    private String connectionString;
    private String zkUrl;
    // 表配置
    private String tableName;
    private List<String> columns;         // 目的表的所有列的列名，包括主键和非主键，不包括时间列
    /**
     * 禁止直接实例化本类，必须调用{@link #parse}接口来初始化
     */
    private HbaseSQLReaderConfig()
    {
    }

    /**
     * @param dataxCfg datax configure
     * @return HbaseSQLReaderConfig
     */
    public static HbaseSQLReaderConfig parse(Configuration dataxCfg)
    {
        assert dataxCfg != null;
        HbaseSQLReaderConfig cfg = new HbaseSQLReaderConfig();
        cfg.originalConfig = dataxCfg;

        // 1. 解析集群配置
        parseClusterConfig(cfg, dataxCfg);

        // 2. 解析列配置
        parseTableConfig(cfg, dataxCfg);

        // 4. 打印解析出来的配置
        LOG.info("HBase SQL reader config parsed: {}", cfg);

        return cfg;
    }

    private static void parseClusterConfig(HbaseSQLReaderConfig cfg, Configuration dataxCfg)
    {
        // 获取hbase集群的连接信息字符串
        String hbaseCfg = dataxCfg.getString(Key.HBASE_CONFIG);
        if (StringUtils.isBlank(hbaseCfg)) {
            // 集群配置必须存在且不为空
            throw AddaxException.asAddaxException(
                    HbaseSQLReaderErrorCode.REQUIRED_VALUE, "读 Hbase 时需要配置hbaseConfig，其内容为 Hbase 连接信息，请查看 Hbase 集群信息.");
        }

        // 解析zk服务器和znode信息
        Pair<String, String> zkCfg;
        try {
            zkCfg = HbaseSQLHelper.getHbaseConfig(hbaseCfg);
        }
        catch (Throwable t) {
            // 解析hbase配置错误
            throw AddaxException.asAddaxException(
                    HbaseSQLReaderErrorCode.REQUIRED_VALUE, "解析hbaseConfig出错，请确认您配置的hbaseConfig为合法的json数据格式，内容正确.");
        }
        String zkQuorum = zkCfg.getFirst();
        String znode = zkCfg.getSecond();
        if (zkQuorum == null || zkQuorum.isEmpty()) {
            throw AddaxException.asAddaxException(
                    HbaseSQLReaderErrorCode.ILLEGAL_VALUE, "HBase的hbase.zookeeper.quorum配置不能为空");
        }
        // 生成sql使用的连接字符串， 格式： jdbc:hbase:zk_quorum:2181:/znode_parent
        cfg.connectionString = "jdbc:phoenix:" + zkQuorum;
        cfg.zkUrl = zkQuorum + ":2181";
        if (!znode.isEmpty()) {
            cfg.connectionString += cfg.connectionString + ":" + znode;
            cfg.zkUrl += cfg.zkUrl + ":" + znode;
        }
    }

    private static void parseTableConfig(HbaseSQLReaderConfig cfg, Configuration dataxCfg)
    {
        // 解析并检查表名
        cfg.tableName = dataxCfg.getString(Key.TABLE);
        if (cfg.tableName == null || cfg.tableName.isEmpty()) {
            throw AddaxException.asAddaxException(
                    HbaseSQLReaderErrorCode.ILLEGAL_VALUE, "HBase的tableName配置不能为空,请检查并修改配置.");
        }

        // 解析列配置,列为空时，补全所有的列
        cfg.columns = dataxCfg.getList(Key.COLUMN, String.class);
        if (cfg.columns == null) {
            throw AddaxException.asAddaxException(
                    HbaseSQLReaderErrorCode.ILLEGAL_VALUE, "您配置的tableName含有非法字符{0}，请检查您的配置.");
        }
        else if (cfg.columns.isEmpty()) {
            try {
                cfg.columns = HbaseSQLHelper.getPColumnNames(cfg.connectionString, cfg.tableName);
                dataxCfg.set(Key.COLUMN, cfg.columns);
            }
            catch (SQLException e) {
                throw AddaxException.asAddaxException(
                        HbaseSQLReaderErrorCode.GET_PHOENIX_COLUMN_ERROR, "HBase的columns配置不能为空,请添加目标表的列名配置." + e.getMessage(), e);
            }
        }
    }

    public String getZkUrl()
    {
        return zkUrl;
    }

    /**
     * @return 获取原始的datax配置
     */
    public Configuration getOriginalConfig()
    {
        return originalConfig;
    }

    /**
     * @return 获取连接字符串，使用ZK模式
     */
    public String getConnectionString()
    {
        return connectionString;
    }

    /**
     * @return 获取表名
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * @return 返回所有的列，包括主键列和非主键列，但不包括version列
     */
    public List<String> getColumns()
    {
        return columns;
    }

    @Override
    public String toString()
    {
        StringBuilder ret = new StringBuilder();
        // 集群配置
        ret.append("\n[jdbc]");
        ret.append(connectionString);
        ret.append("\n");

        // 表配置
        ret.append("[tableName]");
        ret.append(tableName);
        ret.append("\n");
        ret.append("[column]");
        for (String col : columns) {
            ret.append(col);
            ret.append(",");
        }
        ret.setLength(ret.length() - 1);
        ret.append("\n");

        return ret.toString();
    }
}
