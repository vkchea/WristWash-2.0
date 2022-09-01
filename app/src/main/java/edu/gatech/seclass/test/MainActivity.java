package edu.gatech.seclass.test;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

public class MainActivity extends FragmentActivity {

    /*Interactive*/
    Button buttonStart;
    Button buttonStop;
    TextView washHandMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*Interactive*/
        setContentView(R.layout.activity_main);
        buttonStart = findViewById(R.id.startbutton);
        buttonStop = findViewById(R.id.stopbutton);

        /* Main Program */
        buttonStart.setOnClickListener(view -> {
            washHandMsg = findViewById(R.id.event1);
            washHandMsg.setText("");
            buttonStart.setVisibility(view.GONE);
            Log.println(Log.DEBUG, "debug: ", "Starting MicrophoneSensorThread..");
            buttonStop.setVisibility(view.VISIBLE);
            WorkManager workManager = WorkManager.getInstance(this);
            WorkRequest microphoneWorkRequest =
                    new OneTimeWorkRequest.Builder(MicrophoneSensorWorker.class)
                            .addTag("JOB1")
                            .build();
            workManager.enqueue(microphoneWorkRequest);
            workManager.getWorkInfoByIdLiveData(microphoneWorkRequest.getId())
                    .observe(this, workInfo -> {
                        if (workInfo.getState() != null &&
                                workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            buttonStart.setVisibility(view.VISIBLE);
                            buttonStop.setVisibility(view.GONE);
                            washHandMsg.setText("Wash Your Hands!");
                        }
                    });
        });

        buttonStop.setOnClickListener(view -> {
            WorkManager workManager = WorkManager.getInstance(this);
            workManager.cancelAllWorkByTag("JOB1");
            onStop();
            buttonStop.setVisibility(view.GONE);
            buttonStart.setVisibility(view.VISIBLE);
        });
    }
}