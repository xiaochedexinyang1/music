package com.iflytek.cyber.iot.show.core.model;

import java.util.List;

public class Image {
    public String contentDescription;
    public List<Source> sources;

    public static class Source {
        public String url;
        public String darkBackgroundUrl;
        public String size;
        public long widthPixels;
        public long heightPixels;
    }
}
