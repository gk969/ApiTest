package com.gk969.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.TrafficStats;
import android.os.Handler;

import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.Log;

public class Utils {
    static String TAG = "Utils";

    static public int strSimilarity(String s1, String s2) {
        int len = (s1.length() < s2.length()) ? s1.length() : s2.length();

        int i;
        for(i = 0; i < len; i++) {
            if(s1.charAt(i) != s2.charAt(i)) {
                break;
            }
        }

        return i;
    }

    public static boolean isArrayEquals(byte[] array1, byte[] array2) {
        if(array1.length == array2.length) {
            int len = array2.length;

            for(int i = 0; i < len; i++) {
                if(array1[i] != array2[i]) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    public static File newFile(String filePath) {
        File file = new File(filePath);

        File dir = new File(file.getParent());
        if(!dir.exists()) {
            dir.mkdirs();
        }

        return file;
    }

    public static void deleteDir(String dirFilePath) {
        deleteDir(new File(dirFilePath));
    }
    public static void deleteDir(File dirFile) {
        //Log.i(TAG, "deleteDir "+dirFile.getPath());
        if(dirFile == null) {
            return;
        }

        if(!dirFile.exists() || !dirFile.isDirectory()) {
            return;
        }

        File[] files = dirFile.listFiles();
        if(files != null) {
            for(File file : files) {
                if(file.isFile()) {
                    //Log.i(TAG, "deleteFile "+file.getPath());
                    file.delete();
                } else {
                    deleteDir(file);
                }
            }
        }

        dirFile.delete();
    }

    public static void copyFile(File source, File dest) {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(inputChannel != null) {
                    inputChannel.close();
                }

                if(outputChannel != null) {
                    outputChannel.close();
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getCurTimeStr(){
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
    }

    public static File findUniqueFileNameInDir(String dirPath, String fileName, String fileExt) {
        if(!dirPath.endsWith("/")) {
            dirPath += "/";
        }

        File newFile = new File(dirPath + fileName + fileExt);
        if(newFile.exists()) {
            int i = 1;
            while(true) {
                newFile = new File(dirPath + fileName + "(" + i + ")" + fileExt);
                if(!newFile.exists()) {
                    break;
                }
                i++;
            }
        }

        return newFile;
    }

    public static File findUniqueDirNameInDir(String parentDir, String dirName){
        Log.i(TAG, "findUniqueDirNameInDir "+parentDir+" "+dirName);
        if(!parentDir.endsWith("/")) {
            parentDir += "/";
        }

        String newDirPath=parentDir + dirName;
        File newDir = new File(newDirPath);
        if(newDir.exists()) {
            int i = 1;
            while(true) {
                newDir = new File(newDirPath + "(" + i + ")");
                if(!newDir.exists()) {
                    newDir.mkdirs();
                    break;
                }
            }
        }

        return newDir;
    }

    public static String byteArrayToHexString(byte[] arrayIn) {
        if(arrayIn == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder(arrayIn.length * 2);

        for(byte oneByte : arrayIn) {
            builder.append(String.format("%02X", oneByte));
        }

        return builder.toString();
    }

    public static String getFileHashString(String filePath, String algorithm) {
        return byteArrayToHexString(getFileHash(filePath, algorithm));
    }

    public static byte[] getFileHash(String filePath, String algorithm) {
        try {
            return getHash(new FileInputStream(filePath), algorithm);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getStringHashString(String string, String algorithm) {
        return byteArrayToHexString(getStringHash(string, algorithm));
    }

    public static byte[] getStringHash(String string, String algorithm) {
        return getHash(new ByteArrayInputStream(string.getBytes()), algorithm);
    }

    public static byte[] getHash(InputStream inputStream, String algorithm){
        if(inputStream==null){
            return null;
        }

        try {
            MessageDigest digester = MessageDigest.getInstance(algorithm);
            byte[] bytes = new byte[8192];
            int byteCount;
            while((byteCount = inputStream.read(bytes)) > 0) {
                digester.update(bytes, 0, byteCount);
            }

            inputStream.close();
            return digester.digest();
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    public static class NetTrafficCalc {
        private AtomicLong netTrafficPerSec = new AtomicLong(0);
        private long lastTraffic = 0;
        private long refreshTime = 0;
        private Context context;

        private final static int AVERAGE_BUF_SIZE = 5;
        private int avrgBufIndex;
        private long[] averageBuf = new long[AVERAGE_BUF_SIZE];
        private long[] intervalBuf = new long[AVERAGE_BUF_SIZE];

        public NetTrafficCalc(Context ctx) {
            context = ctx;
        }

        public void refreshNetTraffic() {
            long curTraffic = getTotalNetTraffic();
            if(curTraffic != 0) {
                long curTime = SystemClock.uptimeMillis();
                if(lastTraffic != 0) {
                    averageBuf[avrgBufIndex] = (curTraffic - lastTraffic);
                    intervalBuf[avrgBufIndex] = (curTime - refreshTime);

                    avrgBufIndex++;
                    avrgBufIndex %= AVERAGE_BUF_SIZE;

                    long traffic = 0;
                    long time = 0;
                    for(int i = 0; i < AVERAGE_BUF_SIZE; i++) {
                        traffic += averageBuf[i];
                        time += intervalBuf[i];
                    }

                    netTrafficPerSec.set(traffic * 1000 / time);
                }
                refreshTime = curTime;
                lastTraffic = curTraffic;
            }
        }

        private long getTotalNetTraffic() {
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                        context.getPackageName(), PackageManager.GET_ACTIVITIES);
                return TrafficStats.getUidRxBytes(appInfo.uid) + TrafficStats.getUidTxBytes(appInfo.uid);
            } catch(PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            return 0;
        }

        public long getNetTrafficPerSecond(){
            return netTrafficPerSec.get();
        }
    }

    public static class ReadWaitLock {
        public AtomicBoolean isLocked = new AtomicBoolean(false);

        public synchronized void waitIfLocked() {
            while(isLocked.get()) {
                try {
                    wait();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized void lock() {
            while(isLocked.get()) {
                try {
                    wait();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isLocked.set(true);
        }

        public synchronized void unlock() {
            isLocked.set(false);
            notifyAll();
        }
    }

    public static class CubicBezier {
        private static final String TAG = "CubicBezier";
        private static final int POINT_NUM=128;

        private class Point {
            float x;
            float y;

            public Point(float pointX, float pointY) {
                x = pointX;
                y = pointY;
            }
        }

        private Point pointA;
        private Point pointB;

        private Point points[];

        public CubicBezier(float pointAx, float pointAy, float pointBx, float pointBy) {
            Log.i(TAG, pointAx+" "+pointAy+" "+pointBx+" "+pointBy);

            pointAx=valueInRange(pointAx, 1, 0);
            pointBx=valueInRange(pointBx, 1, 0);

            pointA = new Point(pointAx, pointAy);
            pointB = new Point(pointBx, pointBy);

            points=new Point[POINT_NUM];
            for(int i=0; i<POINT_NUM; i++){
                points[i]=getPoint((float)i/(POINT_NUM-1));
            }
        }

        private Point getPoint(float t){
            float tSquared, tCubed, oneDecT, oneDecTSquared;
            float px, py;

            tSquared = t * t;
            tCubed = tSquared * t;

            oneDecT=1-t;
            oneDecTSquared=oneDecT*oneDecT;

            px = 3*pointA.x*t*oneDecTSquared + 3*pointB.x*tSquared*oneDecT + tCubed;
            py = 3*pointA.y*t*oneDecTSquared + 3*pointB.y*tSquared*oneDecT + tCubed;

            //Log.i(TAG, px+" "+py);

            return new Point(px, py);
        }

        public float getY(float x) {
            //Log.i(TAG, "getY x:"+x);

            if(x<=0){
                return 0;
            }

            if(x>=1){
                return 1;
            }

            int start=0;
            int end=POINT_NUM-1;

            while(end-start>1){
                //Log.i(TAG, String.format("start:%d:%f end:%d:%f", start, points[start].x,
                //        end, points[end].x));
                int mid=start+(end-start)/2;
                Point midPoint=points[mid];
                if(x==midPoint.x){
                    return midPoint.y;
                }else if(x>midPoint.x){
                    start=mid;
                }else{
                    end=mid;
                }
            }

            //Log.i(TAG, "points[start].y:"+points[start].y);
            return points[start].y+(points[end].y-points[start].y)*(x-points[start].x)/
                    (points[end].x-points[start].x);
        }

        public static void test(float x1, float y1, float x2, float y2) {
            String str = "";
            Utils.CubicBezier cubicBezier = new Utils.CubicBezier(x1, y1, x2, y2);

            for(int i = 0; i < 100; i++) {
                str += String.format("%.4f,", cubicBezier.getY((float)i/99));
            }
            Log.i(TAG, str);
        }
    }

    private static final String[] SIZE_UNIT_NAME = new String[]{"TB", "GB", "MB", "KB"};
    private static final long[] SIZE_UNIT = new long[]{(long) 1 << 40, 1 << 30, 1 << 20, 1 << 10, 1};
    public static String byteSizeToString(long size) {

        for(int i = 0; i < SIZE_UNIT_NAME.length; i++) {
            if(size >= SIZE_UNIT[i]) {
                size /= SIZE_UNIT[i + 1];
                double sizeInFloat = (double) size / 1024;
                return String.format("%.2f%s", sizeInFloat, SIZE_UNIT_NAME[i]);
            }
        }

        return size + "B";
    }

    public static void saveStringToFile(String str, String filePath) {
        try {
            FileOutputStream fileOut = new FileOutputStream(filePath);
            fileOut.write(str.getBytes());
            fileOut.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static String stringToUnixWrap(String src){
        char[] srcArray=src.toCharArray();
        char[] tarArray=new char[srcArray.length];
        int tarPtr=0;
        for(char curByte:srcArray){
            if(curByte!='\r' && curByte!=0){
                tarArray[tarPtr]=curByte;
                tarPtr++;
            }
        }
        if(tarPtr<srcArray.length){
            char[] finalArray=new char[tarPtr];
            System.arraycopy(tarArray, 0, finalArray, 0, tarPtr);
            tarArray=finalArray;
        }

        return new String(tarArray);
    }

    public static String byteArrayToUnixWrapString(byte[] srcArray){
        byte[] tarArray=new byte[srcArray.length];
        int tarPtr=0;
        for(byte curByte:srcArray){
            if(curByte!='\r' && curByte!=0){
                tarArray[tarPtr]=curByte;
                tarPtr++;
            }
        }
        if(tarPtr<srcArray.length){
            byte[] finalArray=new byte[tarPtr];
            System.arraycopy(tarArray, 0, finalArray, 0, tarPtr);
            tarArray=finalArray;
        }

        return new String(tarArray);
    }

    public static void saveBitmapToFile(Bitmap bmp, File file) {
        FileOutputStream fileOut;
        try {
            fileOut = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fileOut);
            fileOut.flush();
            fileOut.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static String readString(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try {
            byte[] buf = new byte[128];
            int len;
            while((len = inputStream.read(buf)) != -1) {
                byte[] data;
                if(len < buf.length) {
                    data = new byte[len];
                    System.arraycopy(buf, 0, data, 0, len);
                } else {
                    data = buf;
                }

                sb.append(new String(data));
            }
            inputStream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }

    public static String readStringFromFile(File file) {
        String string = null;

        if(file.isFile()) {
            try {
                string = readString(new FileInputStream(file));
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        // Log.i(TAG, "readStringFromFile " + file + " " + string);
        return string;
    }

    public static String readStringFromFile(String filePath) {
        return readStringFromFile(new File(filePath));
    }

    public static String readStringFromHttp(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url)
                    .openConnection();
            if(conn.getResponseCode() == 200) {
                return readString(conn.getInputStream());
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }

        return null;
    }

    public static boolean urlsEqualWithoutProtocol(String srcUrl, String tarUrl){
        if(srcUrl==null || tarUrl==null){
            return false;
        }

        try {
            return srcUrl.substring(srcUrl.indexOf("://")).equals(tarUrl.substring(tarUrl.indexOf("://")));
        }catch(IndexOutOfBoundsException e){
            e.printStackTrace();
            return false;
        }
    }

    public static String removeZeroAtStart(String str){
        for(int i=0; i<str.length(); i++){
            if(str.charAt(i)!='0'){
                return str.substring(i);
            }
        }

        return "";
    }

    public static int parseInt(String string) {
        int value = 0;

        try {
            value = Integer.parseInt(string);
        } catch(NumberFormatException e) {
            e.printStackTrace();
        }

        return value;
    }

    public static float valueInRange(float target, float max, float min){
        if(target>max){
            return max;
        }else if(target<min){
            return min;
        }

        return target;
    }

    public static LinkedList<String> exeShell(String cmd) {
        LinkedList<String> ret = new LinkedList<String>();

        try {
            Process proc = Runtime.getRuntime().exec(cmd);
            String line;
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while((line = inputStream.readLine()) != null) {
                ret.add(line);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }


        return ret;
    }

    public static boolean mayBeUrl(String str) {
        int posOdDot = str.lastIndexOf(".");
        return posOdDot > 0 && posOdDot < (str.length() - 2);
    }

    public static class LogRecorder extends Thread {
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private File recordFile;

        public void stopThread() {
            isRunning.set(false);
        }

        public LogRecorder(String appName) {
            setDaemon(true);
            File logDir = StorageUtils.getDirInSto(appName + "/log");
            if(logDir != null) {
                recordFile = new File(logDir.getPath() + "/log.txt");
                start();
            }
        }

        public void run() {
            Process logcatProcess;

            try {
                Runtime.getRuntime().exec("logcat -c").waitFor();
                logcatProcess = Runtime.getRuntime().exec("logcat");

                BufferedReader logStream = null;
                FileOutputStream logFileOut = null;
                try {
                    logStream = new BufferedReader(
                            new InputStreamReader(logcatProcess.getInputStream()));

                    logFileOut = new FileOutputStream(recordFile.getPath());

                    String logLine;

                    while((logLine = logStream.readLine()) != null) {
                        logLine += "\r\n";
                        logFileOut.write(logLine.getBytes());

                        if(!isRunning.get()) {
                            break;
                        }

                        yield();
                    }
                } finally {
                    if(logStream != null) {
                        logStream.close();
                    }

                    if(logFileOut != null) {
                        logFileOut.flush();
                        logFileOut.close();
                    }
                }
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static int getCpuCoresNum() {
        return new File("/sys/devices/system/cpu/").listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String path = pathname.getName();
                //regex is slow, so checking char by char.
                if(path.startsWith("cpu")) {
                    for(int i = 3; i < path.length(); i++) {
                        if(path.charAt(i) < '0' || path.charAt(i) > '9') {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }
        }).length;
    }

    public static String getSDKVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    public static String getPhoneType() {
        return android.os.Build.MODEL;
    }

    public static boolean isNetworkEffective() {
        String stableWebUrl[] = {"http://www.baidu.com", "http://www.qq.com"};

        for(String webUrl : stableWebUrl) {
            try {
                URL url = new URL(webUrl);

                HttpURLConnection urlConn = (HttpURLConnection) url
                        .openConnection();

                try {
                    urlConn.setConnectTimeout(10000);
                    urlConn.setReadTimeout(5000);

                    if(urlConn.getResponseCode() == 200) {
                        urlConn.disconnect();
                        Log.i(TAG, "isNetworkEffective " + webUrl);
                        return true;
                    }
                } finally {
                    if(urlConn != null)
                        urlConn.disconnect();
                }
            } catch(MalformedURLException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * dp、sp 转换为 px 的工具类
     */
    public static class DisplayUtil {
        /**
         * 将px值转换为dip或dp值，保证尺寸大小不变
         */
        public static int pxToDip(Context context, float pxValue) {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (pxValue / scale + 0.5f);
        }

        /**
         * 将dip或dp值转换为px值，保证尺寸大小不变
         */
        public static int dipToPx(Context context, float dipValue) {
            return (int) (context.getResources().getDisplayMetrics().density * dipValue);
        }

        /**
         * 将px值转换为sp值，保证文字大小不变
         */
        public static int pxToSp(Context context, float pxValue) {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (pxValue / fontScale + 0.5f);
        }

        /**
         * 将sp值转换为px值，保证文字大小不变
         */
        public static int spToPx(Context context, float spValue) {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (spValue * fontScale + 0.5f);
        }

        public static int attrToPx(Context context, String attr) {
            int px = 0;
            try {
                int attrVal = Integer.parseInt(attr.substring(0,
                        attr.length() - 2));
                if(attr.endsWith("px")) {
                    px = attrVal;
                } else if(attr.endsWith("dp")) {
                    px = dipToPx(context, attrVal);
                } else if(attr.endsWith("sp")) {
                    px = spToPx(context, attrVal);
                }
            } catch(NumberFormatException e) {
                e.printStackTrace();
            }
            return px;
        }
    }
}