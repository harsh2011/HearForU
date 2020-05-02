package com.hack.hearforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;

import tech.oom.idealrecorder.IdealRecorder;
import tech.oom.idealrecorder.StatusListener;

public class CollectDataActivity extends AppCompatActivity {

    private IdealRecorder idealRecorder = null;
    private IdealRecorder.RecordConfig recordConfig;

    private WaveView waveView;

    private final int SAMPLE_RATE = 48000;
    private File wavFile;
    private MediaPlayer mPlayer = null;

    private Button recordBtn, stoprecordBtn, resetrecordBtn,
            mPlayBtn, mStopBtn;

    private TextView btnSubmit;

    private EditText ageEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect_data);

        init();

        btnSubmit = (TextView) findViewById(R.id.btnSubmit);
        ageEt = (EditText) findViewById(R.id.ageEt);

        waveView = (WaveView) findViewById(R.id.wave_view);

        recordBtn = (Button) findViewById(R.id.recordBtn);
        stoprecordBtn = (Button) findViewById(R.id.stoprecordBtn);
        stoprecordBtn.setVisibility(View.GONE);
        resetrecordBtn = (Button) findViewById(R.id.resetrecordBtn);
        resetrecordBtn.setVisibility(View.GONE);

        mPlayBtn = (Button) findViewById(R.id.mPlayBtn);
        mPlayBtn.setVisibility(View.GONE);
        mStopBtn = (Button) findViewById(R.id.mStopBtn);
        mStopBtn.setVisibility(View.GONE);

        recordConfig = new IdealRecorder.RecordConfig(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadAudio();

            }
        });

        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordBtn.setVisibility(View.GONE);
                resetrecordBtn.setVisibility(View.GONE);
                stoprecordBtn.setVisibility(View.VISIBLE);
                mPlayBtn.setVisibility(View.GONE);
                mStopBtn.setVisibility(View.GONE);
                record();
            }
        });

        stoprecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stoprecordBtn.setVisibility(View.GONE);
                resetrecordBtn.setVisibility(View.VISIBLE);
                recordBtn.setVisibility(View.GONE);
                mPlayBtn.setVisibility(View.VISIBLE);
                mStopBtn.setVisibility(View.GONE);
                stopRecord();
            }
        });

        resetrecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stoprecordBtn.setVisibility(View.GONE);
                resetrecordBtn.setVisibility(View.GONE);
                recordBtn.setVisibility(View.VISIBLE);
                mPlayBtn.setVisibility(View.GONE);
                mStopBtn.setVisibility(View.GONE);
                waveView.clear();
                reset();
                init();
            }
        });

        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stoprecordBtn.setVisibility(View.GONE);
                resetrecordBtn.setVisibility(View.VISIBLE);
                recordBtn.setVisibility(View.GONE);
                mPlayBtn.setVisibility(View.GONE);
                mStopBtn.setVisibility(View.VISIBLE);
                mPlayer = new MediaPlayer();
                try {
                    mPlayer.setDataSource(wavFile.getAbsolutePath());
                    mPlayer.prepare();
                    mPlayer.start();
                    playAllAsync();
                } catch (IOException e) {
                    Log.e("Audio", "prepare() failed");
                }
            }
        });

        mStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stoprecordBtn.setVisibility(View.GONE);
                resetrecordBtn.setVisibility(View.VISIBLE);
                recordBtn.setVisibility(View.GONE);
                mPlayBtn.setVisibility(View.VISIBLE);
                mStopBtn.setVisibility(View.GONE);
                mPlayer.stop();
                mPlayer = null;
            }
        });

        mDatabaseReference = FirebaseDatabase.getInstance().getReference().child("cough Research Data");
        mCoughStorageReference = FirebaseStorage.getInstance().getReference().child("cough");

        initCovidRadio();
        initCough();
        initFever();

    }

    private DatabaseReference mDatabaseReference;
    private StorageReference mCoughStorageReference;

    private void uploadAudio() {

        Uri audioFileUri = Uri.fromFile(wavFile);
        StorageReference filepath = mCoughStorageReference.child(System.currentTimeMillis()
                + ".wav");


        filepath.putFile(audioFileUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Task<Uri> firebasePhotoUri = taskSnapshot.getStorage().getDownloadUrl();
                firebasePhotoUri.addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {

                        Data coughData = new Data(sCovid,bFever,thermometerEt.getText().toString(),Integer.parseInt(ageEt.getText().toString()),bCough, uri.toString());
                        mDatabaseReference.push().setValue(coughData);

                        Toast.makeText(CollectDataActivity.this, "Upload successful, Thank you!", Toast.LENGTH_LONG).show();

                    }

                });
            }
        });
    }

    public void playAllAsync() {
        Thread r = new Thread() {


            @Override
            public void  run() {

                while (mPlayer != null) {
                    if (!mPlayer.isPlaying()){
                        break;
                    }
                    try {
                        Thread.sleep(100,0); // lower cpu if we wait and not just busy wait
                    } catch (InterruptedException e) { e.printStackTrace();}
                }
                raisePlayBackFinishEvent();

            }
        };
        r.start();
    }

    private void raisePlayBackFinishEvent(){
        if(mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStopBtn.setVisibility(View.GONE);
                mPlayBtn.setVisibility(View.VISIBLE);
            }
        });

    }

    private void init(){
        IdealRecorder.getInstance().init(this);
        idealRecorder = IdealRecorder.getInstance();
    }
    private void reset(){
        idealRecorder = null;
    }
    private void record() {
        idealRecorder.setRecordFilePath(getSaveFilePath());
        idealRecorder.setRecordConfig(recordConfig).setMaxRecordTime(20000).setVolumeInterval(200);
        idealRecorder.setStatusListener(statusListener);
        idealRecorder.start();
    }
    private void stopRecord() {
        idealRecorder.stop();
    }
    private String getSaveFilePath() {
        File file = new File(getExternalCacheDir(), "Audio");
        if (!file.exists()) {
            file.mkdirs();
        }
        wavFile = new File(file, "cough.wav");
        return wavFile.getAbsolutePath();
    }

    private StatusListener statusListener = new StatusListener() {

        @Override
        public void onRecordData(short[] data, int length) {

            for (int i = 0; i < length; i += 60) {
                waveView.addData(data[i]);
            }
        }

        @Override
        public void onFileSaveFailed(String error) {
            Toast.makeText(CollectDataActivity.this, "Something went wrong, Please try again.", Toast.LENGTH_SHORT).show();
        }

    };

    private String sCovid;

    private RadioButton mPositive;
    private RadioButton mNegative;
    private RadioButton mNotTested;
    private RadioButton mWaiting;

    public void initCovidRadio(){
        mPositive = (RadioButton) findViewById(R.id.positive);
        mNegative = (RadioButton) findViewById(R.id.negative);
        mNotTested = (RadioButton) findViewById(R.id.not_tested);
        mWaiting = (RadioButton) findViewById(R.id.waiting_for_result);
    }

    public void onCovidRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.positive:
                if (checked)
                    sCovid = mPositive.getText().toString();

                break;
            case R.id.negative:
                if (checked)
                    sCovid = mNegative.getText().toString();
                break;

            case R.id.not_tested:
                if (checked)
                    sCovid = mNotTested.getText().toString();
                break;

            case R.id.waiting_for_result:
                if (checked)
                    sCovid = mWaiting.getText().toString();
                break;
        }

    }

    private boolean bFever;

    private RadioButton mFeverNo;
    private RadioButton mFeverYes;
    private EditText thermometerEt;

    public void initFever(){
        mFeverYes = (RadioButton) findViewById(R.id.f_yes);
        mFeverNo = (RadioButton) findViewById(R.id.f_no);
        thermometerEt = (EditText) findViewById(R.id.thermometerEt);
        thermometerEt.setVisibility(View.GONE);
    }

    public void onFeverButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.f_no:
                if (checked)
                    thermometerEt.setVisibility(View.GONE);
                    bFever = false;

                break;
            case R.id.f_yes:
                if (checked)
                    thermometerEt.setVisibility(View.VISIBLE);
                    bFever = true;
                break;
        }

    }

    private boolean bCough;

    private RadioButton mCoughNo;
    private RadioButton mCoughYes;
    private LinearLayout recordLayout;

    public void initCough(){
        mCoughYes = (RadioButton) findViewById(R.id.c_yes);
        mCoughNo = (RadioButton) findViewById(R.id.c_no);
        recordLayout= (LinearLayout) findViewById(R.id.recordLayout);
        recordLayout.setVisibility(View.GONE);
    }

    public void onCoughButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.c_no:
                if (checked)
                    recordLayout.setVisibility(View.GONE);
                    bCough = false;

                break;
            case R.id.c_yes:
                if (checked)
                    recordLayout.setVisibility(View.VISIBLE);
                    bCough = true;
                break;
        }
    }

}
