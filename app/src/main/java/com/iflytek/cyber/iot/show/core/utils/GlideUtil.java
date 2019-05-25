package com.iflytek.cyber.iot.show.core.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;

public class GlideUtil {

    public static void show(@NonNull Context context, String path, @NonNull ImageView target) {
        Glide.with(context).load(new File(path))
                .thumbnail(0.5f)
                .into(target);
    }

    public static void show(@NonNull Context context, Bitmap bitmap, @NonNull ImageView target) {
        Glide.with(context).load(bitmap)
                .thumbnail(0.5f)
                .into(target);
    }

    public static void show(@NonNull Context context, File file, @NonNull ImageView target) {
        Glide.with(context).load(file)
                .thumbnail(0.5f)
                .into(target);
    }

    public static void loadBitmap(Context context, byte[] bytes, ImageView imageView) {
        try {
            Glide.with(context)
                    .load(bytes)
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadImage(Context context, String url, int erroId, ImageView imageView) {
        //设置图片圆角角度
        RequestOptions options = new RequestOptions()
                .placeholder(erroId)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        try {
            Glide.with(context)
                    .load(url)
                    .apply(options)
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadUrlImage(Context context, String url, int erroId, ImageView imageView) {
        //设置图片圆角角度
        RoundedCorners roundedCorners = new RoundedCorners(12);
//通过RequestOptions扩展功能,override:采样率,因为ImageView就这么大,可以压缩图片,降低内存消耗
        RequestOptions options = RequestOptions.bitmapTransform(roundedCorners).placeholder(erroId);
        try {
            Glide.with(context)
                    .load(url)
                    .apply(options)
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载圆形图片
     *
     * @param context
     * @param url
     * @param erroId
     * @param imageView
     */
    public static void load(Context context, String url, int erroId, ImageView imageView) {
        RequestOptions requestOptions = RequestOptions.circleCropTransform()
                .placeholder(erroId)
                .error(erroId)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        try {
            Glide.with(context)
                    .load(url)
                    .apply(requestOptions)
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
