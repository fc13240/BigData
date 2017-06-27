package com.wyd.BigData.RDD;
import com.wyd.BigData.bean.AccountInfo;
import com.wyd.BigData.bean.LoginInfo;
import com.wyd.BigData.bean.LoginSumInfo;
import com.wyd.BigData.bean.PlayerInfo;
import com.wyd.BigData.dao.BaseDao;
import com.wyd.BigData.util.DataType;
import com.wyd.BigData.util.StringUtil;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.Optional;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import scala.Tuple2;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
public class LoginRDD implements Serializable {
    /**
     *
     */
    private static final long             serialVersionUID = -758442520627154431L;
    private static       SimpleDateFormat sf               = new SimpleDateFormat("yyyy_MM_dd");
    private static final String           DATATYPE         = String.valueOf(DataType.MARKNUM_LOGIN);

    public void call(JavaRDD<String[]> rdd) {
        JavaRDD<String[]> loginRDD = rdd.filter(parts -> parts.length > 2 && DATATYPE.equals(parts[0]));
        // 数据源去重
        JavaRDD<Row> loginRowRDD = loginRDD.map(parts -> {
            String day = sf.format(new Date(Long.parseLong(parts[1])));
            return RowFactory.create(Integer.valueOf(parts[2]), Integer.valueOf(parts[3]), Integer.valueOf(parts[5]), day);
        }).distinct();
        JavaPairRDD<String, Iterable<Row>> groupRowRDD = loginRowRDD.groupBy(row -> row.getString(3));
        Map<String, List<Row>> dayRowMap = new HashMap<>();
        groupRowRDD.collect().forEach(f -> {
            String day = f._1();
            Iterable<Row> rows = f._2();
            List<Row> list = new ArrayList<>();
            rows.forEach(list::add);
            dayRowMap.put(day, list);
        });
        JavaSparkContext jsc = new JavaSparkContext(rdd.context());
        for (String today : dayRowMap.keySet()) {
            JavaRDD<Row> loginDayRowRDD = jsc.parallelize(dayRowMap.get(today));
            // 查询数据库
            BaseDao baseDao = BaseDao.getInstance();
            List<LoginSumInfo> loginInfoFromDBList = baseDao.getAllLoginSumInfo(today);
            if (loginInfoFromDBList.size() > 0) {
                // 把登陆表里的所有数据查出变成RDD，格式是KEY：playerId,Value:true
                List<Tuple2<Integer, Boolean>> userFromDBList = new ArrayList<>();
                for (LoginSumInfo info : loginInfoFromDBList) {
                    userFromDBList.add(new Tuple2<>(info.getPlayerId(), true));
                }
                JavaPairRDD<Integer, Boolean> userFromDbRDD = jsc.parallelizePairs(userFromDBList);
                // 把数据来源RDD变成KEY：playerId,Value: 数据本身
                JavaPairRDD<Integer, Row> rdd2Pair = loginDayRowRDD.mapToPair(row -> new Tuple2<>(row.getInt(2), row));
                JavaPairRDD<Integer, Tuple2<Row, Optional<Boolean>>> joined = rdd2Pair.leftOuterJoin(userFromDbRDD);
                JavaPairRDD<Integer, Tuple2<Row, Optional<Boolean>>> filtered = joined.filter(data -> {
                    Optional<Boolean> optional = data._2._2;
                    return !optional.isPresent() || !optional.get();
                });
                // LogLog.debug("count3=====" + filtered.count());
                JavaPairRDD<Integer, Row> rdd2filtered = filtered.mapToPair(data -> new Tuple2<>(data._1, data._2._1));
                rdd2filtered.foreachPartition(rows -> {
                    BaseDao dao = BaseDao.getInstance();
                    Row data;
                    List<LoginSumInfo> loginList = new ArrayList<>();
                    while (rows.hasNext()) {
                        data = rows.next()._2;
                        loginList.add(new LoginSumInfo(data.getInt(0), data.getInt(1), data.getInt(1), data.getInt(2)));
                    }
                    dao.saveLoginSumInfoBatch(today, loginList);
                });
            } else {
                loginDayRowRDD.foreachPartition(rows -> {
                    BaseDao dao = BaseDao.getInstance();
                    Row data;
                    List<LoginSumInfo> loginList = new ArrayList<>();
                    while (rows.hasNext()) {
                        data = rows.next();
                        loginList.add(new LoginSumInfo(data.getInt(0), data.getInt(1), data.getInt(1), data.getInt(2)));
                    }
                    dao.saveLoginSumInfoBatch(today, loginList);
                });
            }
        }
        //更新playerInfo,loginInfo
        List<String[]> loginLogList = loginRDD.collect();
        BaseDao dao = BaseDao.getInstance();
        List<PlayerInfo> playerInfoList = new ArrayList<>();
        List<LoginInfo> loginInfoList = new ArrayList<>();
        for (String[] datas : loginLogList) {
            int playerId = Integer.parseInt(datas[5]);
            PlayerInfo playerInfo = dao.getPlayerInfo(playerId);
            if (playerInfo == null) {
                continue;
            }
            Date dataTime = new Date(Long.parseLong(datas[1]));
            playerInfo.setLoginTime(dataTime);
            playerInfo.setLoginNum(playerInfo.getLoginNum() + 1);
            LoginInfo login = new LoginInfo();
            login.setServiceId(Integer.parseInt(datas[2]));
            login.setChannelId(Integer.parseInt(datas[3]));
            login.setAccountId(Integer.parseInt(datas[4]));
            login.setPlayerId(Integer.parseInt(datas[5]));
            login.setDeviceMac(StringUtil.filterOffUtf8Mb4(datas[6]));
            login.setDeviceName(StringUtil.filterOffUtf8Mb4(datas[7]));
            login.setSystemName(StringUtil.filterOffUtf8Mb4(datas[8]));
            login.setPlayerChannel(playerInfo.getChannelId());
            if (datas.length > 9) {
                login.setSystemVersion(datas[9]);
                login.setAppVersion(datas[10]);
            }
            login.setLoginTime(dataTime);
            if (datas.length > 11) {
                login.setLoginIp(datas[11]);
            }
            if (datas.length > 12) {
                int diamond = Integer.parseInt(datas[12]), gold = Integer.parseInt(datas[13]), vigor = Integer.parseInt(datas[14]);
                login.setDiamond(diamond);
                login.setGold(gold);
                login.setVigor(vigor);
                playerInfo.setDiamond(diamond);
                playerInfo.setGold(gold);
                playerInfo.setVigor(vigor);
            }
            login.setPlayerLevel(playerInfo.getPlayerLevel());
            login.setPlayerName(playerInfo.getPlayerName());
            AccountInfo accountInfo = dao.getAccountInfo(Integer.parseInt(datas[4]));
            if (accountInfo != null) {
                login.setAccountName(accountInfo.getAccountName());
            }
            playerInfoList.add(playerInfo);
            loginInfoList.add(login);
        }
        dao.updatePlayerLoginInfoBatch(playerInfoList);
        dao.saveLoginInfoBatch(loginInfoList);
    }
}
