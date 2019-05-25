package com.iflytek.cyber.iot.show.core.view.viewpage;

import android.support.v4.view.ViewPager;
import android.widget.ImageView;


import com.iflytek.cyber.iot.show.core.R;

import java.util.ArrayList;
import java.util.List;

public class JazzyPagerlistener implements ViewPager.OnPageChangeListener {
    private List<HomeAdInfo> data = new ArrayList<>();
    private ImageView[] arr;

    public JazzyPagerlistener(List<HomeAdInfo> bannerList, ImageView[] arr1) {
        this.arr = arr1;
        this.data = bannerList;
    }

    @Override
    public void onPageScrollStateChanged(int arg0) {

    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {

    }

    @Override
    public void onPageSelected(int arg0) {
        if (data.size() > 0) {
            setImageBackground(arg0 % data.size(), arr);
        }
    }

    /**
     * 设置选中的tip的背景
     * 遍历圆点集体，选中项为白点，其他皆设置灰点
     */
    private void setImageBackground(int selectItems, ImageView[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (i == selectItems) {
                arr[i].setBackgroundResource(R.drawable.banner_dot_bg_active);
            } else {
                arr[i].setBackgroundResource(R.drawable.banner_dot_bg);
            }
        }
    }
}