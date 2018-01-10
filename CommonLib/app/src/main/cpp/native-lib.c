#include <jni.h>
#include <stdlib.h>
#include <malloc.h>
#include <jpeglib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>
#include <android/log.h>
#include <setjmp.h>
#include <cdjpeg.h>     /* Common decls for cjpeg/djpeg applications */
#include <android/bitmap.h>

#define LOG_TAG "jni"
#define LOGW(...)  __android_log_write(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define true 1
#define false 0


typedef uint8_t BYTE;

struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
};

typedef struct my_error_mgr *my_error_ptr;

METHODDEF(void)
my_error_exit(j_common_ptr
              cinfo) {
    my_error_ptr myerr = (my_error_ptr) cinfo->err;
    (*cinfo->err->output_message)(cinfo);
    LOGE("jpeg_message_table[%d]:%s", myerr->pub.msg_code,myerr->pub.jpeg_message_table[myerr->pub.msg_code]);

    longjmp(myerr
                    ->setjmp_buffer, 1);
}

//图片压缩方法
int generateJPEG(BYTE *data, int w, int h, int quality, const char *name, boolean optimize) {
    int nComponent = 3;
    struct jpeg_compress_struct jcs;
    //自定义的error
    struct my_error_mgr jem;

    jcs.err = jpeg_std_error(&jem.pub);
    jem.pub.error_exit = my_error_exit;

    if (setjmp(jem.setjmp_buffer)) {
        return 0;
    }
    //为JPEG对象分配空间并初始化
    jpeg_create_compress(&jcs);
    //获取文件信息
    FILE *f = fopen(name, "wb");
    if (f == NULL) {
        return 0;
    }

    //指定压缩数据源
    jpeg_stdio_dest(&jcs, f);
    jcs.image_width = w;
    jcs.image_height = h;

    jcs.arith_code = false;
    jcs.input_components = nComponent;
    jcs.in_color_space = JCS_RGB;

    jpeg_set_defaults(&jcs);
    jcs.optimize_coding = optimize;

    //为压缩设定参数，包括图像大小，颜色空间
    jpeg_set_quality(&jcs, quality, true);
    //开始压缩
    jpeg_start_compress(&jcs, true);
    JSAMPROW row_point[1];
    int row_stride;
    row_stride = jcs.image_width * nComponent;
    while (jcs.next_scanline < jcs.image_height) {
        row_point[0] = &data[jcs.next_scanline * row_stride];
        jpeg_write_scanlines(&jcs, row_point, 1);
    }

    if (jcs.optimize_coding) {
        LOGI("使用了哈夫曼算法完成压缩");
    } else {
        LOGI("未使用哈夫曼算法");
    }
    //压缩完毕
    jpeg_finish_compress(&jcs);
    //释放资源
    jpeg_destroy_compress(&jcs);
    fclose(f);
    return 1;
}


JNIEXPORT jboolean JNICALL Java_com_nowy_commonlib_JpegUtil_compressBitmap(JNIEnv *env, jclass type, jobject bitmap,
                                                jint width,jint height, jstring fileName,jint quality,jboolean optimize) {
    AndroidBitmapInfo infoColor;
    BYTE *pixelColor;
    BYTE *data;
    BYTE *tempData;
//    const char *filename = env->GetStringUTFChars(fileName, 0);
    const char *filename = (*env)->GetStringUTFChars(env, fileName, NULL);
    if ((AndroidBitmap_getInfo(env, bitmap, &infoColor)) < 0) {
        LOGE("解析错误");
        return false;
    }

    if ((AndroidBitmap_lockPixels(env, bitmap, (void **) &pixelColor)) < 0) {
        LOGE("加载失败");
        return false;
    }

    BYTE r, g, b;
    int color;
    data = (BYTE *) malloc(width * height * 3);
    tempData = data;

    // 将bitmap转换为rgb数据
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            color = *((int *) pixelColor);
            r = ((color & 0x00FF0000) >> 16);//与操作获得rgb，参考java Color定义alpha color >>> 24 red (color >> 16) & 0xFF
            g = ((color & 0x0000FF00) >> 8);
            b = color & 0X000000FF;

            *data = b;
            *(data + 1) = g;
            *(data + 2) = r;
            data += 3;
            pixelColor += 4;
        }
    }

     AndroidBitmap_unlockPixels(env, bitmap);
     int resultCode = generateJPEG(tempData, width, height, quality, filename, optimize);

     free(tempData);
     if (resultCode == 0) {
              return false;
          }

      return true;
}






