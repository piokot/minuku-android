package labelingStudy.nctu.minuku.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.opencsv.CSVWriter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import labelingStudy.nctu.minuku.manager.MinukuStreamManager;
import labelingStudy.nctu.minuku.model.DataRecord.ActivityRecognitionDataRecord;
import labelingStudy.nctu.minuku.streamgenerator.ActivityRecognitionStreamGenerator;
import labelingStudy.nctu.minukucore.exception.StreamNotFoundException;

/**
 * Created by Lawrence on 2017/5/22.
 */

public class ActivityRecognitionService extends IntentService {

    private final String TAG = "ActivityRecognitionService";

    private String Latest_mMostProbableActivitytype;
    private DetectedActivity mMostProbableActivity;
    private List<DetectedActivity> mProbableActivities;
    private CSVWriter csv_writer = null;


    //for saving a set of activity records
    private static ArrayList<ActivityRecognitionDataRecord> mActivityRecognitionRecords;

    public static ActivityRecognitionStreamGenerator mActivityRecognitionStreamGenerator;

    private Timer ARRecordExpirationTimer;
    private Timer ReplayTimer;
    private TimerTask ARRecordExpirationTimerTask;
    private TimerTask ReplayTimerTask;

    //private static String detectedtime;
    private long detectedtime;

    private Boolean updateOrNot = false;

    private static Context serviceInstance = null;

    private SharedPreferences sharedPrefs;

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");

        Log.d("ARService", "[test AR service start]ActivityRecognitionService starts again! !");

        if (!isServiceRunning()){
            serviceInstance = this;
            Log.d("ARService", "[test AR service start] the servic is not running, start replay");

            //testing the record in csv file
//            startReplayARRecordTimer();
        }else {
            Log.d("ARService", "[test AR service start] the servic is arleady running, do not start replay");
        }

//        startARRecordExpirationTimer();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Log.d(TAG, "[test replay] entering onHandleIntent");

        /**  move to TriggerManager  **/
        //TODO triggerManager situationManager, triggerManager: replace ModeWork.work. , situationManager: replace ModeWork.condition. 放transportationManager(In Minuku).
        if(ActivityRecognitionResult.hasResult(intent)) {

            try {
                mActivityRecognitionStreamGenerator = (ActivityRecognitionStreamGenerator) MinukuStreamManager.getInstance().getStreamGeneratorFor(ActivityRecognitionDataRecord.class);
            }catch (StreamNotFoundException e){
                e.printStackTrace();
            }
            ActivityRecognitionResult activity = ActivityRecognitionResult.extractResult(intent);

            mProbableActivities = activity.getProbableActivities();
            mMostProbableActivity = activity.getMostProbableActivity();
            detectedtime = new Date().getTime();

            Log.d(TAG, "[test replay] [test ActivityRecognition]" +   mMostProbableActivity.toString());
            try {
                if (mProbableActivities != null && mMostProbableActivity != null){

                     /*  cancel setting because we want to directly feed activity data in the test file */
                    mActivityRecognitionStreamGenerator.setActivitiesandDetectedtime(mProbableActivities, mMostProbableActivity, detectedtime);

                    Log.d(TAG, "[test replay] before store to CSV in AR Service");

                }

            }catch(Exception e){

            }

            stopARRecordExpirationTimer();

            startARRecordExpirationTimer();
        }
    }

    public void RePlayActivityRecordTimerTask() {


        Log.d("ARService", "[test AR service start]RePlayActivityRecordTimerTask starts again! !");

        ReplayTimerTask = new TimerTask() {

            int activityRecordCurIndex = 0;
            int sec = 0;
            public void run() {

                sec++;

                //for every 5 seconds and if we still have more AR labels in the list to reply, we will set an AR label to the streamgeneratro
                if(sec%5 == 0 && mActivityRecognitionRecords.size()>0 && activityRecordCurIndex < mActivityRecognitionRecords.size()-1){

                    try {
                        mActivityRecognitionStreamGenerator = (ActivityRecognitionStreamGenerator) MinukuStreamManager.getInstance().getStreamGeneratorFor(ActivityRecognitionDataRecord.class);


                        ActivityRecognitionDataRecord activityRecognitionDataRecord = mActivityRecognitionRecords.get(activityRecordCurIndex);

                        mProbableActivities = activityRecognitionDataRecord.getProbableActivities();
                        mMostProbableActivity = activityRecognitionDataRecord.getMostProbableActivity();
                        detectedtime = new Date().getTime(); //TODO might be wrong, be aware for it!!

                        Log.d("ARService", "[test replay] test trip going to feed " +   activityRecognitionDataRecord.getDetectedtime() +  " :"  +  activityRecognitionDataRecord.getProbableActivities()  +  " : " +activityRecognitionDataRecord.getMostProbableActivity()    + " at index " + activityRecordCurIndex  + " to the AR streamgenerator");

                        //user the record from mActivityRecognitionRecords to update the  mActivityRecognitionStreamGenerator
                        mActivityRecognitionStreamGenerator.setActivitiesandDetectedtime(mProbableActivities, mMostProbableActivity, detectedtime);

                        //move on to the next activity Record
                        activityRecordCurIndex++;



                    }catch (StreamNotFoundException e){
                        e.printStackTrace();
                    }


                }

            }
        };


    }


    /** create NA activity label when it's over 10 minutes not receiving an AR label
     * the timeer is reset when the onHandleEvent receives a label**/
    public void initializeARRecordExpirationTimerTask() {

        ARRecordExpirationTimerTask = new TimerTask() {

            int sec = 0;
            public void run() {

                sec++;

                //if counting until ten minutes
                if(sec == 10*60){

                    Log.d("ARService", "[test replay] it's time to create NA activity because not receiving for a long time"  );
                    try {
                        mActivityRecognitionStreamGenerator = (ActivityRecognitionStreamGenerator) MinukuStreamManager.getInstance().getStreamGeneratorFor(ActivityRecognitionDataRecord.class);
                    }catch (StreamNotFoundException e){
                        e.printStackTrace();
                    }

                    ActivityRecognitionDataRecord activityRecognitionDataRecord
                            = new ActivityRecognitionDataRecord();
                    //update the empty AR to MinukuStreamManager
                    MinukuStreamManager.getInstance().setActivityRecognitionDataRecord(activityRecognitionDataRecord);

                }

            }
        };
    }

    public void startARRecordExpirationTimer() {

        //set a new Timer
        ARRecordExpirationTimer = new Timer();

        //initialize the TimerTask's job
        initializeARRecordExpirationTimerTask();

        //schedule the timer, after the first 5000ms the TimerTask will run every 10000ms
        ARRecordExpirationTimer.schedule(ARRecordExpirationTimerTask,0,1000);

    }

    public void stopARRecordExpirationTimer() {
        //stop the timer, if it's not already null
        if (ARRecordExpirationTimer != null) {
            ARRecordExpirationTimer.cancel();
            ARRecordExpirationTimer = null;
        }
    }

    public void startReplayARRecordTimer() {

        Log.d("ARService", "[test AR service start] startReplayARRecordTimer starts !");

        //set a new Timer
        ReplayTimer = new Timer();

        //start the timertask for replay
        RePlayActivityRecordTimerTask();

        //schedule the timer, after the first 5000ms the TimerTask will run every 10000ms
        ReplayTimer.schedule(ReplayTimerTask,0,1000);

    }




    public static boolean isServiceRunning() {
        return serviceInstance != null;
    }

    public long getCurrentTimeInMillis(){
        //get timzone
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tz);
        long t = cal.getTimeInMillis();
        return t;
    }

    public static void addActivityRecognitionRecord(ActivityRecognitionDataRecord record) {
        getActivityRecognitionRecords().add(record);
//        Log.d("ARService", "[test replay] adding " +   record.toString()  + " to ActivityRecognitionRecords in ActivityRecognitionService");
    }

    public static ArrayList<ActivityRecognitionDataRecord> getActivityRecognitionRecords() {

        if (mActivityRecognitionRecords==null){
            mActivityRecognitionRecords = new ArrayList<ActivityRecognitionDataRecord>();
        }
        return mActivityRecognitionRecords;

    }

}
