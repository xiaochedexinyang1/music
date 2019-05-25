package com.iflytek.cyber.iot.show.core.view.viewpage;

import com.google.gson.annotations.Expose;

import java.io.Serializable;

public class HomeAdInfo implements Serializable {

    /**
     * imageUrl : http://beta.kkcredit.cn/news/1.jpg
     * newsId : 1
     * newsUrl : http://beta.kkcredit.cn/news/1.html
     */
    @Expose
    private String imageUrl;
    @Expose
    private int newsId;
    @Expose
    private String newsUrl;

    private boolean isExamplePic = false;

    public HomeAdInfo(String imageUrl, int newsId, String newsUrl) {
        this.imageUrl = imageUrl;
        this.newsId = newsId;
        this.newsUrl = newsUrl;
    }

    public boolean isExamplePic() {
        return isExamplePic;
    }

    public void setExamplePic(boolean examplePic) {
        isExamplePic = examplePic;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getNewsId() {
        return newsId;
    }

    public void setNewsId(int newsId) {
        this.newsId = newsId;
    }

    public String getNewsUrl() {
        return newsUrl;
    }

    public void setNewsUrl(String newsUrl) {
        this.newsUrl = newsUrl;
    }
}
