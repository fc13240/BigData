package com.wyd.BigData.bean;
/**
 * Created by root on 6/7/17.
 */
public class UpgradeInfo implements java.io.Serializable {
    private static final long serialVersionUID = -7702379351620099346L;
    private int  id;
    private int  serviceId;
    private int  playerLevel;
    private long totalTime;
    private int  totalCount;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public int getPlayerLevel() {
        return playerLevel;
    }

    public void setPlayerLevel(int playerLevel) {
        this.playerLevel = playerLevel;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
