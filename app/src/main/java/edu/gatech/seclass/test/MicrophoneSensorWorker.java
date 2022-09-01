package edu.gatech.seclass.test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class MicrophoneSensorWorker extends Worker {

    /*Settings for AudioRecord*/
    int sampleRateInHz = 44100;
    int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, RECORDER_AUDIO_ENCODING);
    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format
    AudioRecord audioRecord = null;

    /*Feature Extraction*/
    ArrayList<Short> samples = new ArrayList<>();
    ArrayList<Short> maxAmpl = new ArrayList<>();
    ArrayList<Double> meanAmpl = new ArrayList<>();
    ArrayList<Double> toiletFlush = new ArrayList<>();
    boolean flushSpike = false;
    boolean flushDrain = false;
    boolean flushFinish = false;
    double flushSpikeMax;
    double flushDrainMin;
    double flushDrainMax;
    ArrayList<Double> flushDrainAcc = new ArrayList<>();
    double flushDrainMean;
    int startingBuff = 1;

    private Context context;

    public MicrophoneSensorWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @Override
    public ListenableWorker.Result doWork() {
        Log.println(Log.DEBUG, "debug", "STARTING RECORDING");

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRateInHz, channelConfig,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
        try {
            short[] buffer = new short[bufferSizeInBytes / 4];
            audioRecord.startRecording();
            String filename = "myfile.csv";
            FileOutputStream outputStream;
            outputStream = context.openFileOutput(filename, Context.MODE_APPEND);

            int frameLen = 200;
            while (true) {
                if(isStopped()){
                    outputStream.close();
                    return Result.failure();
                }
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSizeInBytes / 4);
                //Initial microphone buffer data read (unfiltered)
                for (int i = 0; i < bufferReadResult; i++) {
                    samples.add(buffer[i]);
                    //Collect amplitude of buffer every 200 samples
                    if (i > 0 && (i % frameLen == 0)) {
                        maxAmpl.add(Collections.max(samples));
                        samples.clear();
                        i = 0;
                    }
                    //setting to %101 eliminates duplicates
                    if (maxAmpl.size() > 0 && (maxAmpl.size() % 101 == 0)) {
                        if (startingBuff != 1) {
                                /*Adding max amplitude to a list that will be used to find the mean after ~9000 samples,
                                    which is just about less than .02s*/
                            meanAmpl.add(maxAmpl.stream().mapToDouble(a -> a).average().getAsDouble());
                            maxAmpl.clear();
                                /*Once the list hits around 900 samples, we get the mean value of the current list
                                    to a list called: toiletFlush*/
                            if (meanAmpl.size() > 0 && (meanAmpl.size() % 9 == 0)) {
                                toiletFlush.add(meanAmpl.stream().mapToDouble(a->a).average().getAsDouble());
                                if (toiletFlush.size() > 0 && (toiletFlush.size() % 10 == 0) && flushSpike == false) {
                                    flushSpikeMax = Collections.max(toiletFlush);
                                    if(toiletFlush.get(0)/flushSpikeMax >= .001
                                            && toiletFlush.get(0)/flushSpikeMax <= .04 && flushSpikeMax >= 1900){
                                        flushSpikeMax = Collections.max(toiletFlush);
                                        flushSpike = true;
                                        Log.println(Log.DEBUG, "flushSpike: ", "TRUE");
                                        toiletFlush.clear();
                                    } else {
                                        toiletFlush.remove(0);
                                    }
                                }
                                if(toiletFlush.size() > 5 && (toiletFlush.size() % 50 == 0)
                                        && flushSpike == true && flushDrain == false){
                                    Log.println(Log.DEBUG, "flushDrain: ", "PROCESSING...");
                                    flushDrainMax = Collections.max(toiletFlush);
                                    if(flushDrainMax > flushSpikeMax){
                                        flushSpikeMax = flushDrainMax;
                                    }
                                    flushDrainMin = Collections.min(toiletFlush.subList(15, 50));
                                    flushDrainMax = Collections.max(toiletFlush.subList(15, 50));
//                                        Log.println(Log.DEBUG, "flushSpikeMax: ", "" + flushSpikeMax);
//                                        Log.println(Log.DEBUG, "flushDrainMin: ", "" + flushDrainMin);
//                                        Log.println(Log.DEBUG, "flushDrainMax: ", "" + flushDrainMax);
                                    for(int j = 6; j < toiletFlush.size(); j++){
//                                            Log.println(Log.DEBUG, "min/max: ", "" + toiletFlush.get(j)/flushSpikeMax);
                                        if(toiletFlush.get(j)/flushSpikeMax >= flushDrainMin/flushSpikeMax
                                                && toiletFlush.get(j)/flushSpikeMax <= flushDrainMax/flushSpikeMax
                                                && flushDrainMin >= 500){
                                            flushDrainAcc.add(toiletFlush.get(j));
                                        }
                                    }
                                    Log.println(Log.DEBUG, "Inbound Acc %: ", "" + (flushDrainAcc.size()/45.0)*100);
                                    if(((flushDrainAcc.size()/45.0)*100) >= 80){
                                        flushDrainMean = toiletFlush.stream().mapToDouble(a->a).average().getAsDouble();
                                        flushDrain = true;
                                        flushDrainAcc.clear();
                                        Log.println(Log.DEBUG, "flushDrain: ", "TRUE");
//                                            activity.setContentView(R.layout.activity_main);
//                                            washHandMsg.setText("Wash Your Hands!");
                                        outputStream.close();
                                        return Result.success();
                                    } else {
                                        flushDrainAcc.clear();
                                        toiletFlush.clear();
                                        flushSpike = false;
                                        outputStream.close();
                                    }
                                }
                                if(toiletFlush.size() > 0 && (toiletFlush.size() % 120 == 0)
                                        && flushSpike == true && flushDrain == true && flushFinish == false){
                                    Log.println(Log.DEBUG, "flushFinish: ", "PROCESSING...");
                                    Log.println(Log.DEBUG, "flushFinish Ratio: ", "" + flushDrainMean/Collections.max(toiletFlush));
                                    if(flushDrainMean/Collections.max(toiletFlush) >= .2
                                            && flushDrainMean/Collections.max(toiletFlush) <= .6){
                                        Log.println(Log.DEBUG, "flushDrain: ", "TRUE");
                                        toiletFlush.clear();
                                        flushSpike = false;
                                        flushDrain = false;
                                    } else {
                                        toiletFlush.clear();
                                        flushSpike = false;
                                        flushDrain = false;
                                    }
                                }
//                                    try {
//                                        s = meanAmpl.stream().mapToDouble(a->a).average().getAsDouble() + "\n";
//                                        outputStream.write(s.getBytes());
//                                    } catch (FileNotFoundException fileNotFoundException) {
//                                        fileNotFoundException.printStackTrace();
//                                    }
                                meanAmpl.clear();
                            }

                        }
                        if (startingBuff == 1) {
                            startingBuff++;
                            maxAmpl.clear();
                            meanAmpl.clear();
                            toiletFlush.clear();
                        }
                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
