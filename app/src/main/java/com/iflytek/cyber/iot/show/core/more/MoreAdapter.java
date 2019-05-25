package com.iflytek.cyber.iot.show.core.more;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.iflytek.cyber.iot.show.core.R;

public class MoreAdapter extends RecyclerView.Adapter<MoreAdapter.VHodler> {

    private Context context;
    private String[] titles = new String[]{"社区邻里", "服务城", "健康小蜜", "我的订单",
            "问健康", "在线问诊", "视频通讯", "日程单",
            "戏曲", "有声书", "周边药房", "上门维修",
            "养老顾问", "心里咨询", "天气", "视频"};
    private String[] subTitles = new String[]
            {"最新活动", "我要预定服务", "查看健康情况", "我要查订单",
                    "头晕怎么办", "随时问医生", "我要视频通话", "今天的日程有什么",
                    "我要听戏曲", "读书给我听", "我要买药", "找人维修",
                    "我有养老问题", "找个心理师", "今天天气怎么样", "看电影霸王别姬"};
    private int[] images = new int[]{R.drawable.icon_sqll, R.drawable.icon_fwc, R.drawable.icon_jlkxm, R.drawable.icon_wddd,
            R.drawable.icon_wjk, R.drawable.icon_zxwz, R.drawable.icon_sptx, R.drawable.icon_rcd,
            R.drawable.icon_xq, R.drawable.icon_yss, R.drawable.icon_zbyf, R.drawable.icon_smwx,
            R.drawable.icon_ylgw, R.drawable.icon_xlzx, R.drawable.icon_tq, R.drawable.icon_sp};

    public MoreAdapter(Context context) {
        this.context = context;
    }


    @NonNull
    @Override
    public VHodler onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        VHodler vHodler = null;
        vHodler = new VHodler(LayoutInflater.from(context).inflate(R.layout.item_fragment_more, viewGroup, false));
        return vHodler;
    }

    @Override
    public void onBindViewHolder(@NonNull VHodler vHodler, int posion) {
        vHodler.more_subtitle.setText(subTitles[posion]);
        vHodler.more_title.setText(titles[posion]);
        vHodler.more_image.setImageResource(images[posion]);

    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    public class VHodler extends RecyclerView.ViewHolder {
        public TextView more_title;
        public TextView more_subtitle;
        public ImageView more_image;

        public VHodler(@NonNull View itemView) {
            super(itemView);
            more_title = itemView.findViewById(R.id.more_title);
            more_subtitle = itemView.findViewById(R.id.more_subtitle);
            more_image = itemView.findViewById(R.id.more_image);
        }
    }
}
