package com.hack.hearforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class UploadActivity extends AppCompatActivity {

    private CardView startbtn, stopbtn, resetbtn,
            playbtn, stopplay;

    private static String mFileName = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        getSupportActionBar().hide();

        startbtn = (CardView) findViewById(R.id.btnRecord);
        stopbtn = (CardView) findViewById(R.id.btnStop);
        resetbtn = (CardView) findViewById(R.id.btnReset);

        playbtn = (CardView) findViewById(R.id.btnPlay);
        stopplay = (CardView) findViewById(R.id.btnStopPlay);

        String audio = "Audio";

        mFileName = getExternalFilesDir(audio).getAbsolutePath();
        mFileName += "/AudioRecording.mp3";

        System.out.println(mFileName);

        stopbtn.setVisibility(View.GONE);
        resetbtn.setVisibility(View.GONE);

        playbtn.setVisibility(View.GONE);
        stopplay.setVisibility(View.GONE);


        startbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopbtn.setVisibility(View.GONE);
                resetbtn.setVisibility(View.GONE);

                playbtn.setVisibility(View.GONE);
                stopplay.setVisibility(View.GONE);

                initializeMediaRecord();
                startAudioRecording();
            }
        });

        stopbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAudioRecording();
            }
        });

        resetbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mediaRecorder != null){
                    mediaRecorder = null;
                }
                stopbtn.setVisibility(View.GONE);
                resetbtn.setVisibility(View.GONE);

                playbtn.setVisibility(View.GONE);
                stopplay.setVisibility(View.GONE);

                initializeMediaRecord();
                startAudioRecording();
            }
        });

        playbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopplay.setVisibility(View.VISIBLE);
                playbtn.setVisibility(View.GONE);
                startPlayer();
                playAllAsync();
            }
        });

        stopplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopplay.setVisibility(View.GONE);
                playbtn.setVisibility(View.VISIBLE);
                stopPlayer();
            }
        });


    }
    public void playAllAsync() {
        Thread r = new Thread() {


            @Override
            public void  run() {

                while (mediaPlayer != null) {
                    if (!mediaPlayer.isPlaying()){
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
        if(mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopplay.setVisibility(View.GONE);
                playbtn.setVisibility(View.VISIBLE);
            }
        });

    }


    private void startAudioRecording(){
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(this,"Started",Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this,""+e.toString(),Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
        startbtn.setVisibility(View.GONE);
        stopbtn.setVisibility(View.VISIBLE);
    }

    private void stopAudioRecording(){
        if(mediaRecorder != null){
            try{
                mediaRecorder.stop();
            }catch(RuntimeException stopException){
                Toast.makeText(this,"Lol"+stopException.toString(),Toast.LENGTH_SHORT).show();
            }

            mediaRecorder.release();
            mediaRecorder = null;
        }
        startbtn.setVisibility(View.GONE);
        stopbtn.setVisibility(View.GONE);
        resetbtn.setVisibility(View.VISIBLE);

        playbtn.setVisibility(View.VISIBLE);
        stopplay.setVisibility(View.GONE);
    }

    private MediaPlayer mediaPlayer;

    private void mediaPlayerPlaying(){
        if(!mediaPlayer.isPlaying()){
            stopPlayer();
        }
    }

    private void stopPlayer(){
        if(mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }


    private void startPlayer(){
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(mFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.start();

    }

    private void initializeMediaRecord(){
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(mFileName);
        mediaRecorder.setAudioSamplingRate(1600);
    }


    private MediaRecorder mediaRecorder;



}
