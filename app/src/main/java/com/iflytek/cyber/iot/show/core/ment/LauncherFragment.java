package com.iflytek.cyber.iot.show.core.ment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.iflytek.cyber.iot.show.core.R;
import com.iflytek.cyber.iot.show.core.fragment.BaseFragment;
import com.iflytek.cyber.iot.show.core.more.MoreActivity;
import com.iflytek.cyber.iot.show.core.view.viewpage.HomeAdInfo;
import com.iflytek.cyber.iot.show.core.view.viewpage.JazzyPagerAdapter;
import com.iflytek.cyber.iot.show.core.view.viewpage.JazzyPagerlistener;
import com.iflytek.cyber.iot.show.core.view.viewpage.JazzyViewPager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LauncherFragment extends BaseFragment implements View.OnClickListener {

    //    // 是否继续
    private static boolean theSecondisContinue = true;
    JazzyViewPager jvp_the_second;
    LinearLayout home_sencond_viewGroup;
    private ImageView[] tipsThSencond;
    private List<HomeAdInfo> bannerList = new ArrayList<>();
    // 计时器任务
    private ImageTimerTask theSecondTimeTaks;
    // 计时器
    private Timer theSecondGallery;
    // 广告栏滚动时间
    private int PhotoChangeSecondtime = 6000;

    //第一个添加原点标志
    private boolean theSecondadvertiseAddPointFlag = false;


    private LinearLayout ll_more;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
//        init();
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);
        jvp_the_second = view.findViewById(R.id.jvp_the_second);
        home_sencond_viewGroup = view.findViewById(R.id.home_sencond_viewGroup);
        ll_more = view.findViewById(R.id.ll_more);
        ll_more.setOnClickListener(this);
        init();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    private void init() {
        theSecondGallery = new Timer();
        theSecondTimeTaks = new ImageTimerTask(2);
        theSecondGallery.scheduleAtFixedRate(theSecondTimeTaks, PhotoChangeSecondtime, PhotoChangeSecondtime);
        bannerList.add(new HomeAdInfo("", 0, ""));
        bannerList.add(new HomeAdInfo("", 1, ""));
        bannerList.add(new HomeAdInfo("", 2, ""));
        setupTheSecondJazziness(JazzyViewPager.TransitionEffect.Standard, bannerList);
    }

    // 为广告栏加载数据，配置效果及绑定事件处理类
    private void setupTheSecondJazziness(JazzyViewPager.TransitionEffect effect, List<HomeAdInfo> banner) {

        tipsThSencond = new ImageView[banner.size()];

        if (theSecondadvertiseAddPointFlag) {

            home_sencond_viewGroup.removeAllViews();
        }
        // 根据数据元素个数，动态添加小圆点
        for (int i = 0; i < tipsThSencond.length; i++) {
            ImageView imageView = new ImageView(getActivity());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(5, 5));
            tipsThSencond[i] = imageView;
            if (i == 0) {
                tipsThSencond[i].setBackgroundResource(R.drawable.banner_dot_bg_active);
            } else {
                tipsThSencond[i].setBackgroundResource(R.drawable.banner_dot_bg);
            }
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    new ViewGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
            layoutParams.leftMargin = 10;
            layoutParams.rightMargin = 10;
            home_sencond_viewGroup.addView(imageView, layoutParams);
            theSecondadvertiseAddPointFlag = true;
        }

        if (tipsThSencond.length == 1) {
            home_sencond_viewGroup.setVisibility(View.INVISIBLE);
            jvp_the_second.setPagingEnabled(false);
        } else {
            home_sencond_viewGroup.setVisibility(View.VISIBLE);
            jvp_the_second.setPagingEnabled(true);
        }

        // 广告栏配置JazzyPagerAdapter
        jvp_the_second.setAdapter(new JazzyPagerAdapter(jvp_the_second, getContext(), banner));

        jvp_the_second.setRemainder(banner.size());
        jvp_the_second.setTransitionEffect(effect);

        // 获取广告栏在设备中的显示宽度，通过长宽比计算出广告栏占用高度


        jvp_the_second.addOnPageChangeListener(new JazzyPagerlistener(banner, tipsThSencond));

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ll_more:
//
                Intent mIntent = new Intent(getActivity(), MoreActivity.class);
                startActivity(mIntent);
                getActivity().overridePendingTransition(R.anim.enter_form_bom, R.anim.exit_form_top);
                break;
        }
    }

    // 计时器(正常每隔1秒滚动一次，如遇手工滑动则暂停自动滚动)
    private class ImageTimerTask extends TimerTask {

        int type;

        public ImageTimerTask(int type) {
            this.type = type;
        }

        public void run() {
            switch (type) {
                case 2:
                    if (theSecondisContinue && bannerList.size() != 1) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (bannerList != null && bannerList.size() > 1) {
                                    setImageBackground(jvp_the_second.getCurrentItem() % bannerList.size(), tipsThSencond);
                                    jvp_the_second.setCurrentItem(jvp_the_second.getCurrentItem() + 1);
                                }
                            }
                        }, 0);
                    }
                    break;
            }
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());


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
