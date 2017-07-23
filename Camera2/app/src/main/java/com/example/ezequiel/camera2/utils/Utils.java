package com.example.ezequiel.camera2.utils;

/**
 * Created by Ezequiel Adrian on 24/02/2017.
 */

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

public class Utils {

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static int getScreenHeight(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
    }

    public static int getScreenWidth(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    public static float getScreenRatio(Context c) {
        DisplayMetrics metrics = c.getResources().getDisplayMetrics();
        return ((float)metrics.heightPixels / (float)metrics.widthPixels);
    }

    public static int getScreenRotation(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay().getRotation();
    }

    public static int distancePointsF(PointF p1, PointF p2) {
        return (int) Math.sqrt((p1.x - p2.x) *  (p1.x - p2.x) + (p1.y - p2.y) *  (p1.y - p2.y));
    }

    public static PointF middlePoint(PointF p1, PointF p2) {
        if(p1 == null || p2 == null)
            return null;
        return new PointF((p1.x+p2.x)/2, (p1.y+p2.y)/2);
    }

    public static Size[] sizeToSize(android.util.Size[] sizes) {
        Size[] size = new Size[sizes.length];
        for(int i=0; i<sizes.length; i++) {
            size[i] = new Size(sizes[i].getWidth(), sizes[i].getHeight());
        }
        return size;
    }


    public static void testSaveRawImage(Size s, byte [] mPendingFrameData)
    {
        File _filesdir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String createfilepath = new File(_filesdir, Calendar.getInstance().getTimeInMillis() + ".png").getAbsolutePath();

        BufferedOutputStream bos = null;
        try {
            //ByteBuffer imagedata = outputFrame.getGrayscaleImageData().duplicate();
            YuvImage yuvimage = new YuvImage(mPendingFrameData, ImageFormat.NV21, s.getWidth(), s.getHeight(), null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, s.getWidth(), s.getHeight()), 100, baos); // Where 100 is the quality of the generated jpeg
            byte[] jpegArray = baos.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);

            bos = new BufferedOutputStream(new FileOutputStream(createfilepath));

            bitmap.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bitmap.recycle();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (Exception ex){
            String a = ex.toString();
        }
        finally {
            if (bos != null) try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    public static void testSavebitmap(Bitmap bitmap){
        File _filesdir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String createfilepath = new File(_filesdir, Calendar.getInstance().getTimeInMillis() + ".png").getAbsolutePath();

        BufferedOutputStream bos = null;
        try {

            //Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);

            bos = new BufferedOutputStream(new FileOutputStream(createfilepath));

            bitmap.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bitmap.recycle();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (Exception ex){
            String a = ex.toString();
        }
        finally {
            if (bos != null) try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
