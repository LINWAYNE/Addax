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

package com.wgzhao.addax.common.element;

import com.wgzhao.addax.common.exception.CommonErrorCode;
import com.wgzhao.addax.common.exception.AddaxException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

/**
 * Created by jingxing on 14-8-24.
 */
public class DateColumn
        extends Column
{

    private DateType subType = DateType.DATETIME;

    /**
     * 构建值为null的DateColumn，使用Date子类型为DATETIME
     *
     */
    public DateColumn()
    {
        this((Long) null);
    }

    /**
     * 构建值为stamp(Unix时间戳)的DateColumn，使用Date子类型为DATETIME
     * 实际存储有date改为long的ms，节省存储
     *
     * @param stamp 时间戳
     */
    public DateColumn( Long stamp)
    {
        super(stamp, Column.Type.DATE, (null == stamp ? 0 : 8));
    }

    /*
     * 构建值为date(java.util.Date)的DateColumn，使用Date子类型为DATETIME
     */
    public DateColumn( Date date)
    {
        this(date == null ? null : date.getTime());
    }

    /*
     * 构建值为date(java.sql.Date)的DateColumn，使用Date子类型为DATE，只有日期，没有时间
     */
    public DateColumn( java.sql.Date date)
    {
        this(date == null ? null : date.getTime());
        this.setSubType(DateType.DATE);
    }

    /*
     * 构建值为time(java.sql.Time)的DateColumn，使用Date子类型为TIME，只有时间，没有日期
     */
    public DateColumn( java.sql.Time time)
    {
        this(time == null ? null : time.getTime());
        this.setSubType(DateType.TIME);
    }

    /*
     * 构建值为ts(java.sql.Timestamp)的DateColumn，使用Date子类型为DATETIME
     */
    public DateColumn( java.sql.Timestamp ts)
    {
        this(ts == null ? null : ts.getTime());
        this.setSubType(DateType.DATETIME);
    }

    @Override
    public Long asLong()
    {

        return (Long) this.getRawData();
    }

    @Override
    public String asString()
    {
        try {
            return ColumnCast.date2String(this);
        }
        catch (Exception e) {
            throw AddaxException.asAddaxException(
                    CommonErrorCode.CONVERT_NOT_SUPPORT,
                    String.format("Date[%s]类型不能转为String .", this.toString()));
        }
    }

    @Override
    public Date asDate()
    {
        if (null == this.getRawData()) {
            return null;
        }

        return new Date((Long) this.getRawData());
    }

    @Override
    public byte[] asBytes()
    {
        throw AddaxException.asAddaxException(
                CommonErrorCode.CONVERT_NOT_SUPPORT, "Date类型不能转为Bytes .");
    }

    @Override
    public Boolean asBoolean()
    {
        throw AddaxException.asAddaxException(
                CommonErrorCode.CONVERT_NOT_SUPPORT, "Date类型不能转为Boolean .");
    }

    @Override
    public Double asDouble()
    {
        throw AddaxException.asAddaxException(
                CommonErrorCode.CONVERT_NOT_SUPPORT, "Date类型不能转为Double .");
    }

    @Override
    public BigInteger asBigInteger()
    {
        throw AddaxException.asAddaxException(
                CommonErrorCode.CONVERT_NOT_SUPPORT, "Date类型不能转为BigInteger .");
    }

    @Override
    public BigDecimal asBigDecimal()
    {
        throw AddaxException.asAddaxException(
                CommonErrorCode.CONVERT_NOT_SUPPORT, "Date类型不能转为BigDecimal .");
    }

    public DateType getSubType()
    {
        return subType;
    }

    public void setSubType(DateType subType)
    {
        this.subType = subType;
    }

    public enum DateType
    {
        DATE, TIME, DATETIME
    }
}