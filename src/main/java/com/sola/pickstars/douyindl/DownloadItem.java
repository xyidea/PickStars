package com.sola.pickstars.douyindl;

public class DownloadItem {

    //1为视频，2为图文-视频，3为图文-图片
    private int itemType;

    private String awemeId;

    private String downloadUrl;

    private String SavePath;

    public DownloadItem(int itemType, String awemeId, String downloadUrl, String getSavePath) {
        this.itemType = itemType;
        this.awemeId = awemeId;
        this.downloadUrl = downloadUrl;
        this.SavePath = getSavePath;
    }

    @Override
    public String toString() {
        return "DownloadItem{" +
                "itemType=" + itemType +
                ", awemeId='" + awemeId + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", SavePath='" + SavePath + '\'' +
                '}';
    }

    public int getItemType() {
        return itemType;
    }

    public String getAwemeId() {
        return awemeId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getSavePath() {
        return SavePath;
    }
}
