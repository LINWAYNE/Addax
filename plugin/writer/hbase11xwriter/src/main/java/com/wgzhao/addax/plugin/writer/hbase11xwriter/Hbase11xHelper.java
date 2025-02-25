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

package com.wgzhao.addax.plugin.writer.hbase11xwriter;

import com.wgzhao.addax.common.exception.AddaxException;
import com.wgzhao.addax.common.util.Configuration;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public class Hbase11xHelper
{

    private static final Logger LOG = LoggerFactory.getLogger(Hbase11xHelper.class);

    private Hbase11xHelper() {}

    public static org.apache.hadoop.conf.Configuration getHbaseConfiguration(String hbaseConfig)
    {
        if (StringUtils.isBlank(hbaseConfig)) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.REQUIRED_VALUE, "读 Hbase 时需要配置hbaseConfig，其内容为 Hbase 连接信息，请联系 Hbase PE 获取该信息.");
        }
        org.apache.hadoop.conf.Configuration hConfiguration = HBaseConfiguration.create();
        try {
            Map<String, String> hbaseConfigMap = JSON.parseObject(hbaseConfig, new TypeReference<Map<String, String>>() {});
            // 用户配置的 key-value 对 来表示 hbaseConfig
            Validate.isTrue(hbaseConfigMap != null, "hbaseConfig不能为空Map结构!");
            for (Map.Entry<String, String> entry : hbaseConfigMap.entrySet()) {
                hConfiguration.set(entry.getKey(), entry.getValue());
            }
        }
        catch (Exception e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.GET_HBASE_CONNECTION_ERROR, e);
        }
        return hConfiguration;
    }

    public static org.apache.hadoop.hbase.client.Connection getHbaseConnection(String hbaseConfig)
    {
        org.apache.hadoop.conf.Configuration hConfiguration = Hbase11xHelper.getHbaseConfiguration(hbaseConfig);

        org.apache.hadoop.hbase.client.Connection hConnection = null;
        try {
            hConnection = ConnectionFactory.createConnection(hConfiguration);
        }
        catch (Exception e) {
            Hbase11xHelper.closeConnection(hConnection);
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.GET_HBASE_CONNECTION_ERROR, e);
        }
        return hConnection;
    }

    public static Table getTable(Configuration configuration)
    {
        String hbaseConfig = configuration.getString(Key.HBASE_CONFIG);
        String userTable = configuration.getString(Key.TABLE);
        long writeBufferSize = configuration.getLong(Key.WRITE_BUFFER_SIZE, Constant.DEFAULT_WRITE_BUFFER_SIZE);
        org.apache.hadoop.hbase.client.Connection hConnection = Hbase11xHelper.getHbaseConnection(hbaseConfig);
        TableName hTableName = TableName.valueOf(userTable);
        org.apache.hadoop.hbase.client.Admin admin = null;
        org.apache.hadoop.hbase.client.Table hTable = null;
        try {
            admin = hConnection.getAdmin();
            Hbase11xHelper.checkHbaseTable(admin, hTableName);
            hTable = hConnection.getTable(hTableName);
            BufferedMutatorParams bufferedMutatorParams = new BufferedMutatorParams(hTableName);
            bufferedMutatorParams.writeBufferSize(writeBufferSize);
        }
        catch (Exception e) {
            Hbase11xHelper.closeTable(hTable);
            Hbase11xHelper.closeAdmin(admin);
            Hbase11xHelper.closeConnection(hConnection);
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.GET_HBASE_TABLE_ERROR, e);
        }
        return hTable;
    }

    public static BufferedMutator getBufferedMutator(Configuration configuration)
    {
        String hbaseConfig = configuration.getString(Key.HBASE_CONFIG);
        String userTable = configuration.getString(Key.TABLE);
        long writeBufferSize = configuration.getLong(Key.WRITE_BUFFER_SIZE, Constant.DEFAULT_WRITE_BUFFER_SIZE);
        org.apache.hadoop.conf.Configuration hConfiguration = Hbase11xHelper.getHbaseConfiguration(hbaseConfig);
        org.apache.hadoop.hbase.client.Connection hConnection = Hbase11xHelper.getHbaseConnection(hbaseConfig);
        TableName hTableName = TableName.valueOf(userTable);
        org.apache.hadoop.hbase.client.Admin admin = null;
        BufferedMutator bufferedMutator = null;
        try {
            admin = hConnection.getAdmin();
            Hbase11xHelper.checkHbaseTable(admin, hTableName);
            //参考HTable getBufferedMutator()
            bufferedMutator = hConnection.getBufferedMutator(
                    new BufferedMutatorParams(hTableName)
                            .pool(HTable.getDefaultExecutor(hConfiguration))
                            .writeBufferSize(writeBufferSize));
        }
        catch (Exception e) {
            Hbase11xHelper.closeBufferedMutator(bufferedMutator);
            Hbase11xHelper.closeAdmin(admin);
            Hbase11xHelper.closeConnection(hConnection);
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.GET_HBASE_BUFFEREDMUTATOR_ERROR, e);
        }
        return bufferedMutator;
    }

    public static void deleteTable(Configuration configuration)
    {
        String userTable = configuration.getString(Key.TABLE);
        LOG.info("HBasWriter begins to delete table {} .", userTable);
        Scan scan = new Scan();
        org.apache.hadoop.hbase.client.Table hTable = Hbase11xHelper.getTable(configuration);
        try (ResultScanner scanner = hTable.getScanner(scan)) {
            for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
                hTable.delete(new Delete(rr.getRow()));
            }
        }
        catch (Exception e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.DELETE_HBASE_ERROR, e);
        }
        finally {
            Hbase11xHelper.closeTable(hTable);
        }
    }

    public static void truncateTable(Configuration configuration)
    {
        String hbaseConfig = configuration.getString(Key.HBASE_CONFIG);
        String userTable = configuration.getString(Key.TABLE);
        LOG.info("HBasWriter begins to truncate table {} .", userTable);
        TableName hTableName = TableName.valueOf(userTable);
        org.apache.hadoop.hbase.client.Connection hConnection = Hbase11xHelper.getHbaseConnection(hbaseConfig);
        org.apache.hadoop.hbase.client.Admin admin = null;
        try {
            admin = hConnection.getAdmin();
            Hbase11xHelper.checkHbaseTable(admin, hTableName);
            admin.disableTable(hTableName);
            admin.truncateTable(hTableName, true);
        }
        catch (Exception e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.TRUNCATE_HBASE_ERROR, e);
        }
        finally {
            Hbase11xHelper.closeAdmin(admin);
            Hbase11xHelper.closeConnection(hConnection);
        }
    }

    public static void closeConnection(Connection hConnection)
    {
        try {
            if (null != hConnection) {
                hConnection.close();
            }
        }
        catch (IOException e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.CLOSE_HBASE_CONNECTION_ERROR, e);
        }
    }

    public static void closeAdmin(Admin admin)
    {
        try {
            if (null != admin) {
                admin.close();
            }
        }
        catch (IOException e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.CLOSE_HBASE_AMIN_ERROR, e);
        }
    }

    public static void closeBufferedMutator(BufferedMutator bufferedMutator)
    {
        try {
            if (null != bufferedMutator) {
                bufferedMutator.close();
            }
        }
        catch (IOException e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.CLOSE_HBASE_BUFFEREDMUTATOR_ERROR, e);
        }
    }

    public static void closeTable(Table table)
    {
        try {
            if (null != table) {
                table.close();
            }
        }
        catch (IOException e) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.CLOSE_HBASE_TABLE_ERROR, e);
        }
    }

    private static void checkHbaseTable(Admin admin, TableName hTableName)
            throws IOException
    {
        if (!admin.tableExists(hTableName)) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "HBase源头表" + hTableName.toString()
                    + "不存在, 请检查您的配置 或者 联系 Hbase 管理员.");
        }
        if (!admin.isTableAvailable(hTableName)) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "HBase源头表" + hTableName.toString()
                    + " 不可用, 请检查您的配置 或者 联系 Hbase 管理员.");
        }
        if (admin.isTableDisabled(hTableName)) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "HBase源头表" + hTableName.toString()
                    + "is disabled, 请检查您的配置 或者 联系 Hbase 管理员.");
        }
    }

    public static void validateParameter(Configuration originalConfig)
    {
        originalConfig.getNecessaryValue(Key.HBASE_CONFIG, Hbase11xWriterErrorCode.REQUIRED_VALUE);
        originalConfig.getNecessaryValue(Key.TABLE, Hbase11xWriterErrorCode.REQUIRED_VALUE);

        Hbase11xHelper.validateMode(originalConfig);

        String encoding = originalConfig.getString(Key.ENCODING, Constant.DEFAULT_ENCODING);
        if (!Charset.isSupported(encoding)) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, String.format("Hbasewriter 不支持您所配置的编码:[%s]", encoding));
        }
        originalConfig.set(Key.ENCODING, encoding);

        Boolean walFlag = originalConfig.getBool(Key.WAL_FLAG, false);
        originalConfig.set(Key.WAL_FLAG, walFlag);
        long writeBufferSize = originalConfig.getLong(Key.WRITE_BUFFER_SIZE, Constant.DEFAULT_WRITE_BUFFER_SIZE);
        originalConfig.set(Key.WRITE_BUFFER_SIZE, writeBufferSize);
    }

    private static void validateMode(Configuration originalConfig)
    {
        String mode = originalConfig.getNecessaryValue(Key.MODE, Hbase11xWriterErrorCode.REQUIRED_VALUE);
        ModeType modeType = ModeType.getByTypeName(mode);
        if (modeType == ModeType.NORMAL) {
            validateRowkeyColumn(originalConfig);
            validateColumn(originalConfig);
            validateVersionColumn(originalConfig);
        }
        else {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE,
                    String.format("Hbase11xWriter不支持该 mode 类型:%s", mode));
        }
    }

    private static void validateColumn(Configuration originalConfig)
    {
        List<Configuration> columns = originalConfig.getListConfiguration(Key.COLUMN);
        if (columns == null || columns.isEmpty()) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.REQUIRED_VALUE, "column为必填项，其形式为：column:[{\"index\": 0,\"name\": \"cf0:column0\",\"type\": \"string\"},{\"index\": 1,\"name\": \"cf1:column1\",\"type\": \"long\"}]");
        }
        for (Configuration aColumn : columns) {
            Integer index = aColumn.getInt(Key.INDEX);
            String type = aColumn.getNecessaryValue(Key.TYPE, Hbase11xWriterErrorCode.REQUIRED_VALUE);
            String name = aColumn.getNecessaryValue(Key.NAME, Hbase11xWriterErrorCode.REQUIRED_VALUE);
            ColumnType.getByTypeName(type);
            if (name.split(":").length != 2) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, String.format("您column配置项中name配置的列格式[%s]不正确，name应该配置为 列族:列名  的形式, 如 {\"index\": 1,\"name\": \"cf1:q1\",\"type\": \"long\"}", name));
            }
            if (index == null || index < 0) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "您的column配置项不正确,配置项中中index为必填项,且为非负数，请检查并修改.");
            }
        }
    }

    private static void validateRowkeyColumn(Configuration originalConfig)
    {
        List<Configuration> rowkeyColumn = originalConfig.getListConfiguration(Key.ROWKEY_COLUMN);
        if (rowkeyColumn == null || rowkeyColumn.isEmpty()) {
            throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.REQUIRED_VALUE, "rowkeyColumn为必填项，其形式为：rowkeyColumn:[{\"index\": 0,\"type\": \"string\"},{\"index\": -1,\"type\": \"string\",\"value\": \"_\"}]");
        }
        int rowkeyColumnSize = rowkeyColumn.size();
        for (Configuration aRowkeyColumn : rowkeyColumn) {
            Integer index = aRowkeyColumn.getInt(Key.INDEX);
            String type = aRowkeyColumn.getNecessaryValue(Key.TYPE, Hbase11xWriterErrorCode.REQUIRED_VALUE);
            ColumnType.getByTypeName(type);
            if (index == null) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.REQUIRED_VALUE, "rowkeyColumn配置项中index为必填项");
            }
            //不能只有-1列,即rowkey连接串
            if (rowkeyColumnSize == 1 && index == -1) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "rowkeyColumn配置项不能全为常量列,至少指定一个rowkey列");
            }
            if (index == -1) {
                aRowkeyColumn.getNecessaryValue(Key.VALUE, Hbase11xWriterErrorCode.REQUIRED_VALUE);
            }
        }
    }

    private static void validateVersionColumn(Configuration originalConfig)
    {
        Configuration versionColumn = originalConfig.getConfiguration(Key.VERSION_COLUMN);
        //为null,表示用当前时间;指定列,需要index
        if (versionColumn != null) {
            Integer index = versionColumn.getInt(Key.INDEX);
            if (index == null) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.REQUIRED_VALUE, "versionColumn配置项中index为必填项");
            }
            if (index == -1) {
                //指定时间,需要index=-1,value
                versionColumn.getNecessaryValue(Key.VALUE, Hbase11xWriterErrorCode.REQUIRED_VALUE);
            }
            else if (index < 0) {
                throw AddaxException.asAddaxException(Hbase11xWriterErrorCode.ILLEGAL_VALUE, "您versionColumn配置项中index配置不正确,只能取-1或者非负数");
            }
        }
    }
}
