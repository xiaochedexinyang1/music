package com.iflytek.cyber.iot.show.core.more;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;

import com.iflytek.cyber.iot.show.core.R;

public class MoreActivity extends AppCompatActivity {
    private RecyclerView recly_view;
    private LinearLayout image;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        recly_view = findViewById(R.id.recly_view);
        image = findViewById(R.id.image);
        initView();
    }

    private void initView() {
        recly_view.setAdapter(new MoreAdapter(this));
        recly_view.setLayoutManager(new GridLayoutManager(MoreActivity.this, 4));
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.enter_form_bom, R.anim.exit_form_top);

    }
}
