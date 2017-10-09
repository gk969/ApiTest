package com.gk969.Utils;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by songjian on 2016/9/8.
 */
public class StorageUtils {
    private final static String TAG = "StorageUtils";
    private final static int INFO_REFRESH_INTERVAL = 30;
    private final static int VALID_TOTAL_SIZE_MIN = 128 << 20;

    private static class StorageDeviceDir {
        public long freeSpace;
        public long totalSpace;
        public String path;
        public LinkedList<String> pathList;

        public StorageDeviceDir(long pFreeSpace, long pTotalSpace, String firstPath) {
            freeSpace = pFreeSpace;
            totalSpace = pTotalSpace;
            pathList = new LinkedList<>();
            pathList.add(firstPath);
            path = firstPath;
        }
    }

    public class StorageDir {
        public long freeSpace;
        public long totalSpace;
        public String path;

        public StorageDir(long pFreeSpace, long pTotalSpace, String firstPath) {
            freeSpace = pFreeSpace;
            totalSpace = pTotalSpace;
            path = firstPath;
        }
    }

    private LinkedList<StorageDeviceDir> storageDeviceDirList = new LinkedList<>();
    private ReentrantLock storageInfoLock = new ReentrantLock();

    public interface OnGottenStorageDirListener {
        public void onGotten(LinkedList<StorageDir> storageDirs);
    }

    public void getStorageDir(final OnGottenStorageDirListener listener) {
        mScheduledExecutor.execute(new Runnable() {
            @Override
            public void run() {
                storageInfoLock.lock();
                storageDeviceDirList = getStorageInfo();
                storageInfoLock.unlock();

                LinkedList<StorageDir> storageInfo = new LinkedList<>();
                for(StorageDeviceDir storageDeviceDir : storageDeviceDirList) {
                    storageInfo.add(new StorageDir(storageDeviceDir.freeSpace, storageDeviceDir.totalSpace,
                            storageDeviceDir.path));
                }

                listener.onGotten(storageInfo);
            }
        });
    }

    public long getMainStoragFreeSpace(){
        long freeSpace=0;
        storageInfoLock.lock();
        StorageDeviceDir mainStorage=storageDeviceDirList.getFirst();
        if(mainStorage!=null) {
            freeSpace = mainStorage.freeSpace;
        }
        storageInfoLock.unlock();
        return freeSpace;
    }
    
    public long getFreeSpace(String path) {
        long freeSpace = 0;
        storageInfoLock.lock();
        for(StorageDeviceDir info : storageDeviceDirList) {
            if(path.startsWith(info.path)) {
                freeSpace = info.freeSpace;
                break;
            }
        }
        storageInfoLock.unlock();
        return freeSpace;
    }

    public long getMaxFreeSize() {
        long freeSpace = 0;
        storageInfoLock.lock();
        for(StorageDeviceDir info : storageDeviceDirList) {
            if(info.freeSpace > freeSpace) {
                freeSpace = info.freeSpace;
            }
        }
        storageInfoLock.unlock();
        return freeSpace;
    }

    private ScheduledExecutorService mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    public StorageUtils() {
        mScheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                LinkedList<StorageDeviceDir> newStorageDirList = getStorageInfo();
                storageInfoLock.lock();
                storageDeviceDirList = newStorageDirList;
                storageInfoLock.unlock();
            }
        }, 0, INFO_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }

    public void stopRefresh() {
        mScheduledExecutor.shutdown();
    }

    private static LinkedList<StorageDeviceDir> getStorageInfo() {
        LinkedList<StorageDeviceDir> newStorageDirList = new LinkedList<>();
        
        //long startTime = SystemClock.uptimeMillis();
        if(Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED)) {
            File deviceStorage = Environment.getExternalStorageDirectory();
            newStorageDirList.add(new StorageDeviceDir(deviceStorage.getFreeSpace(),
                    deviceStorage.getTotalSpace(), deviceStorage.getPath()));
        }

        //Log.i(TAG, "exec mount");
        LinkedList<String> mountList = Utils.exeShell("cat /proc/mounts");
        for(String mount : mountList) {
            //Log.i(TAG, mount);
            String path = mount.split(" ")[1];
            
            File stoDir = new File(path);
            //Log.i(TAG, "path "+path+" "+stoDir.exists()+" "+stoDir.isDirectory()+" "+stoDir.canWrite());
            if(stoDir.exists() && stoDir.isDirectory() && stoDir.canWrite()) {
                //Log.i(TAG, mount);
                checkAddStorage(stoDir, newStorageDirList);
            }
        }
    
        //findStorageInDir(new File("/storage"), newStorageDirList);
        //findStorageInDir(new File("/mnt"), newStorageDirList);
        
        //Log.i(TAG, "getStorageInfo time " + (SystemClock.uptimeMillis() - startTime));

        /*
        for(StorageDeviceDir storageDeviceDir:newStorageDirList){
            Log.i(TAG, "size "+(storageDeviceDir.freeSpace >> 20) + "/" +
                    (storageDeviceDir.totalSpace >> 20));
            for(String dir:storageDeviceDir.pathList){
                Log.i(TAG, dir);
            }
        }
        */

        return newStorageDirList;
    }
    
    private static boolean checkAddStorage(File stoDir, LinkedList<StorageDeviceDir> newStorageDirList){
        long freeSpace = stoDir.getFreeSpace();
        long totalSpace = stoDir.getTotalSpace();
        //Log.i(TAG, "checkAddStorage "+stoDir + " size:" + (freeSpace >> 20) + "/" + (totalSpace >> 20));
        
        if(totalSpace >= VALID_TOTAL_SIZE_MIN) {
            boolean linkedDirFound = false;
            for(StorageDeviceDir storageDeviceDir : newStorageDirList) {
                if(storageDeviceDir.totalSpace == totalSpace && (Math.abs(storageDeviceDir.freeSpace - freeSpace) < (1 << 20))) {
                    linkedDirFound = true;
                    if(!storageDeviceDir.pathList.contains(stoDir.getPath())) {
                        storageDeviceDir.pathList.add(stoDir.getPath());
                    }
                }
            }
        
            if(!linkedDirFound) {
                newStorageDirList.add(new StorageDeviceDir(freeSpace, totalSpace, stoDir.getPath()));
            }
            
            return true;
        }
        
        return false;
    }
    
    private static void findStorageInDir(File dir, LinkedList<StorageDeviceDir> newStorageDirList){
        Log.i(TAG, "findStorageInDir "+dir.getPath());
        if(dir.exists()&&dir.isDirectory()&&dir.canRead()){
            if(!dir.canWrite()||!checkAddStorage(dir, newStorageDirList)){
                for(File subDir:dir.listFiles()){
                    findStorageInDir(subDir, newStorageDirList);
                }
            }
        }
    }

    public static File[] getAppStoDirs() {
        LinkedList<StorageDeviceDir> newStorageDirList = getStorageInfo();

        File[] stoDir = new File[newStorageDirList.size()];
        for(int i = 0; i < stoDir.length; i++) {
            stoDir[i] = new File(newStorageDirList.get(i).path);
        }

        return stoDir;
    }

    public static File getDirInSto(String path) {
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return getDirInSto(path, Environment.getExternalStorageDirectory().getPath());
        }
        return null;
    }

    public static File getDirInSto(String path, String storageDir) {
        File dir = null;

        if(!path.startsWith("/")) {
            path = "/" + path;
        }

        dir = new File(storageDir + path);
        if(!dir.exists()) {
            Log.i(TAG, "Dir:" + dir.toString() + " Not Exist!");
            dir.mkdirs();
            if(!dir.exists()) {
                return null;
            }
        } else {
            Log.i(TAG, "Dir:" + dir.toString() + " Already Exist!");
        }

        return dir;
    }
}