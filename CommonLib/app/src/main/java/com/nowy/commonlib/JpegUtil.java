package com.nowy.commonlib;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by Nowy on 2018/5/31.
 *
 *
 *<br/>
 * 图片压缩工具类
 * 通过哈夫曼算法进行压缩
 * 提供了两个压缩方法
 *<br/>
 *<br/>
 * 1.compress(Context context,String filePath, String outputPath,int quality)<br/>
 * 根据传入的质量进行图片压缩
 *<br/>
 * 2.compressSize(Context context,String filePath, String outputPath,int targetSize)<br/>
 * 根据传入的目标大小进行图片压缩
 *<br/>
 * native方法为<br/>
 * 3.compressBitmap(Bitmap bit, int w, int h, String fineName,int quality,boolean optimize)
 */
public class JpegUtil {
    static {
        System.loadLibrary("compress");
        System.loadLibrary("jpeg");
    }

    private JpegUtil() {
    }


    /**
     * 哈夫曼图片压缩
     * @param bit 图片bmp
     * @param w 图片的宽
     * @param h 图片的高
     * @param outputPath 文件输出路径(全称)，带上文件名xxx.jpeg
     * @param quality 质量比(1~100);
     * @param optimize 是否启用哈夫曼算法，true为启用
     * @return true 图片压缩成功， false图片压缩失败
     */
    public static native boolean compressBitmap(Bitmap bit, int w, int h, String outputPath,int quality,
                                                boolean optimize);


    /**
     * 通过JNI图片压缩并把Bitmap保存到指定目录
     * @param filePath 源文件路径
     * @param outputPath 输出路径
     * @param quality 这里100表示保质量，还是会压缩
     */
    public static boolean compress(Context context,String filePath, String outputPath,int quality) {
        //根据地址获取bitmap
        Bitmap result = getBitmapFromFile(context,filePath);
        int w = result.getWidth();
        int h = result.getHeight();
        boolean isSuccess = compressBitmap(result,w,h, outputPath,quality, true);
        // 释放Bitmap
        if (!result.isRecycled()) {
            result.recycle();
        }
        return isSuccess;
    }


    /**
     * 通过JNI图片压缩并把Bitmap保存到指定目录，如果原图小于targetSize(kb)，则返回false
     * 注意项，如果原图小于targetSize，不会走压缩，也不会有文件输出
     *
     * 此方法是通过校验压缩结果的大小进行重新压缩，所以压缩耗时为  N(次)压缩时间之和
     * 影响压缩时间的参数是  <targetSize的大小> 和 <quality的递减幅度>
     *
     * @param filePath 原文件路径
     * @param outputPath 输出路径
     * @param targetSize 压缩的目标大小最大值 (例如:300K),单位kb
     */
    public static boolean compressSize(Context context,String filePath, String outputPath,int targetSize) throws Exception{
        boolean isSuccess = false;
        //根据地址获取bitmap
        Bitmap result = getBitmapFromFile(context,filePath);
        int quality = 100;
        int w = result.getWidth();
        int h = result.getHeight();
        long fileSize =  getFileSize(new File(filePath)) /1024;
        while (fileSize > targetSize) {
//            Log.e("compress","压缩中：quality:"+quality);
            isSuccess = compressBitmap(result,w,h, outputPath,quality, true);
            if(isSuccess){
                fileSize = getFileSize(new File(outputPath))/1024;
//                Log.e("compress","压缩中：newFileSize :"+fileSize);
            }else{
                break;
            }
            // 每次都减少10
            quality -= 10;
        }

        // 释放Bitmap
        if (!result.isRecycled()) {
            result.recycle();
        }

        return isSuccess;
    }


    /**
     *
     * 获取图片压缩比率
     * @param context
     * @param bitWidth 图片宽度
     * @param bitHeight 图片高度
     * @return 图片压缩比率
     */
    public static int getRatioSize(Context context,int bitWidth, int bitHeight) {
        // 图片最大分辨率
        int imageHeight = getDeviceHeight(context,1280);
        int imageWidth = getDeviceWidth(context,960);
        // 缩放比
        int ratio = 1;
        // 缩放比,由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        if (bitWidth > bitHeight && bitWidth > imageWidth) {
            // 如果图片宽度比高度大,以宽度为基准
            ratio = bitWidth / imageWidth;
        } else if (bitWidth < bitHeight && bitHeight > imageHeight) {
            // 如果图片高度比宽度大，以高度为基准
            ratio = bitHeight / imageHeight;
        }
        // 最小比率为1
        if (ratio <= 0)
            ratio = 1;
        return ratio;
    }

    /**
     * 通过文件路径读获取Bitmap防止OOM以及解决图片旋转问题
     * @param filePath 文件路径
     * @return 位图数据
     */
    public static Bitmap getBitmapFromFile(Context context,String filePath){
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        newOpts.inJustDecodeBounds = true;//只读边,不读内容
        BitmapFactory.decodeFile(filePath, newOpts);
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        // 获取尺寸压缩倍数
        newOpts.inSampleSize = getRatioSize(context,w,h);
        newOpts.inJustDecodeBounds = false;//读取所有内容
        newOpts.inDither = false;
        newOpts.inPurgeable=true;
        newOpts.inInputShareable=true;
        newOpts.inTempStorage = new byte[1024*1024*5];//创建临时文件，将图片存储5M
        Bitmap bitmap = null;
        File file = new File(filePath);
        FileInputStream fs = null;
        try {
            fs = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if(fs!=null){
                bitmap = BitmapFactory.decodeFileDescriptor(fs.getFD(),null,newOpts);
                //旋转图片
                int photoDegree = readPictureDegree(filePath);
                if(photoDegree != 0){
                    Matrix matrix = new Matrix();
                    matrix.postRotate(photoDegree);
                    // 创建新的图片
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            if(fs!=null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }

    /**
     *
     * 读取图片属性：旋转的角度
     * @param path 图片绝对路径
     * @return degree旋转的角度
     */

    public static int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }


    /**
     * 获取手机屏幕宽度
     * @param context
     * @param def 默认宽度
     * @return 屏幕宽度
     */
    public static int getDeviceWidth(Context context,int def) {
        int width = def;
        WindowManager manager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        if(manager != null ){
            width = manager.getDefaultDisplay().getWidth();
        }
        if(width <= 0) width = def;
        return width;
    }


    /**
     * 获取手机屏幕高度
     * @param context
     * @param def 默认高度
     * @return 屏幕高度
     */
    public static int getDeviceHeight(Context context,int def) {
        int height = def;
        WindowManager manager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        if(manager != null ){
            height = manager.getDefaultDisplay().getHeight();
        }
        if(height <= 0) height = def;
        return height;
    }


    /**
     * 获取指定文件大小
     * @param file
     * @return 文件大小，long类型，单位：b
     * @throws Exception 　　
     */
    public static long getFileSize(File file) throws Exception {
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        } else {
            file.createNewFile();
        }
        return size;
    }




}
