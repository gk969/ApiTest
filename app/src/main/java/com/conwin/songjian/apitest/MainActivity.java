package com.conwin.songjian.apitest;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.gk969.Utils.Utils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Handler handler = new Handler();
    private TextView textCount;
    private volatile boolean running = true;

    private static final String DB_NAME = "test.db";
    private static final String TB_NAME = "urls";

    private static final String URL_SEED = "http://www.58pic.com/tupian/asdasda234444444445555552422asdasdasa4423423423423444sdada/gaoqingtupiansucai.html";
    private static final int URL_LEN_MIN = 20;

    private static final int TOTAL_DATA_LEN = 1000000;
    private static final int COUNT_TEST_NUM = 10;
    private static final int COUNT_TEST_PERIOD = TOTAL_DATA_LEN / COUNT_TEST_NUM;
    private long[] countTime = new long[COUNT_TEST_NUM];
    private long[] countSize = new long[COUNT_TEST_NUM];
    private float[] queryTime = new float[COUNT_TEST_NUM];
    private long testStartTime;

    private class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = "SQLiteOpenHelper";

        DatabaseHelper(Context context) {
            super(context, DB_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.i(TAG, "onCreate");
            db.execSQL("CREATE TABLE "+TB_NAME+" (url varchar(4000) not null, "
                    + "title varchar(4000), "
                    + "container varchar(4000), "
                    + "processed INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "onUpgrade oldVersion "+oldVersion+" newVersion "+newVersion);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            Log.i(TAG, "onOpen");
        }
    }

    private String getCountStr() {
        StringBuilder countStr = new StringBuilder("count group query size\n");
        for (int i = 0; i < COUNT_TEST_NUM; i++) {
            if(countTime[i]==0){
                break;
            }
            long groupTime = i == 0 ? countTime[0] : countTime[i] - countTime[i - 1];
            countStr.append((i+1) * COUNT_TEST_PERIOD).append(" ")
                    .append(groupTime).append("ms ")
                    .append(queryTime[i]).append("ms ")
                    .append(Utils.byteSizeToString(countSize[i]))
                    .append("\n");
        }

        return countStr.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        textCount = (TextView) findViewById(R.id.text_count);

        startTestSqlite();

    }

    void startTestSqlite(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLiteDatabase db = new DatabaseHelper(getApplication()).getWritableDatabase();
                    db.execSQL("DELETE FROM "+TB_NAME);

                    testStartTime = SystemClock.uptimeMillis();

                    for (int i = 0; i < TOTAL_DATA_LEN; i++) {
                        if (!running) {
                            break;
                        }

                        String newUrl = i+URL_SEED.substring(0, (URL_SEED.length() - (int) (Math.random() * (URL_SEED.length() - URL_LEN_MIN))));
                        String insertCmd="INSERT INTO "+TB_NAME+" (url,processed) VALUES ('" + newUrl + "',0)";
                        db.execSQL(insertCmd);
                        //Log.i(TAG, insertCmd);

                        if (((i + 1) % COUNT_TEST_PERIOD) == 0) {
                            int countIndex=i / COUNT_TEST_PERIOD;
                            countTime[countIndex] = SystemClock.uptimeMillis() - testStartTime;
                            countSize[countIndex] = getApplication().getDatabasePath(DB_NAME).length();

                            long queryStart=SystemClock.uptimeMillis();
                            int queryTest=10;
                            for(int t=0; t<queryTest; t++) {
                                Cursor cursor = db.rawQuery("SELECT * FROM " + TB_NAME + " WHERE url = 'asd" + URL_SEED + t+"'", null);
                                Log.i(TAG, "query "+cursor.getCount());
                                cursor.close();
                            }
                            queryTime[countIndex]=(float)(SystemClock.uptimeMillis()-queryStart)/queryTest;

                            final String countStr = getCountStr();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    textCount.setText(countStr);
                                }
                            });
                        }
                    }

                    db.close();
                } catch (SQLiteException e) {
                    e.printStackTrace();
                }

                Log.i(TAG, "sqlite test stop");
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
