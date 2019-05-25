package com.iflytek.cyber.iot.show.core.view.viewpage;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.iflytek.cyber.iot.show.core.R;
import com.iflytek.cyber.iot.show.core.utils.GlideUtil;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by lzj on 2018/9/2.
 */
public class JazzyPagerAdapter extends PagerAdapter {

    private Context context;
    /**
     * JazzyViewPager控件
     */
    private JazzyViewPager mJazzy;
    /**
     * PromotionNewsInfo集合
     */
    private List<HomeAdInfo> mList = new ArrayList<>();

    private List<View> ivList = new ArrayList<>();

    /**
     * 构造函数
     */
    public JazzyPagerAdapter(JazzyViewPager mJazzy, Context context,
                             List<HomeAdInfo> list) {
        this.context = context;
        this.mList = list;
        this.mJazzy = mJazzy;
    }

    @Override
    public int getCount() {
        return Short.MAX_VALUE;
    }


    @Override
    public boolean isViewFromObject(View view, Object o) {
        if (view instanceof OutlineContainer) {
            return ((OutlineContainer) view).getChildAt(0) == o;
        } else {
            return view == o;
        }


    }


    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView(mJazzy.findViewFromObject(position));
        if (mJazzy.findViewFromObject(position) != null) {
        }
    }

    @Override
    public Object instantiateItem(ViewGroup container, final int position) {

        View view = LayoutInflater.from(context).inflate(R.layout.home_banner_item_kkl, null).findViewById(R.id.home_banner_galleryImage_kkl);
        ImageView imageView = view.findViewById(R.id.image);
        container.addView(view);
        ivList.add(view);
        mJazzy.setObjectForPosition(view, position);
        GlideUtil.loadImage(context,mList.get(position % mList.size()).getImageUrl(),R.drawable.ic_default,imageView);
        return view;
    }
}
