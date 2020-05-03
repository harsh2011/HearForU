package com.hack.hearforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class DetectActivity extends AppCompatActivity {

    private static final String LOG_TAG = DetectActivity.class.getSimpleName();



    private CardView btnStart, btnStop, gif, result;

    private TextView result_txt;

    //private WebView wbv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);

        this.getSupportActionBar().hide();


        initiateModel();
        initiatelLabel();
        initiateRecongizeCommands();

        btnStart = (CardView) findViewById(R.id.btnStartDet);
        btnStop = (CardView) findViewById(R.id.btnStopDet);
        gif = (CardView) findViewById(R.id.gif);
        result = (CardView) findViewById(R.id.result);
        result_txt = (TextView) findViewById(R.id.result_txt);


        gif.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gif.setVisibility(View.VISIBLE);
                result.setVisibility(View.VISIBLE);
                btnStop.setVisibility(View.VISIBLE);
                btnStart.setVisibility(View.GONE);
                startRecording();
                startRecognition();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gif.setVisibility(View.GONE);
                result.setVisibility(View.GONE);
                btnStop.setVisibility(View.GONE);
                btnStart.setVisibility(View.VISIBLE);
                stopRecognition();
                stopRecording();
            }
        });

    }

    private static final String LABEL_FILENAME = "file:///android_asset/model_label.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/model_1.tflite";
    private Interpreter tfLite;
    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void initiatelLabel(){
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualLabelFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
                if (line.charAt(0) != '_') {
                    displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
                }
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }
    }

    public void initiateModel(){

        String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
        try {
            tfLite = new Interpreter(loadModelFile(getAssets(), actualModelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
        //tfLite.resizeInput(1, new int[] {1});


    }

    private RecognizeCommands recognizeCommands = null;
    private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
    private static final float DETECTION_THRESHOLD = 0.50f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;



    public void initiateRecongizeCommands(){
        // Set up an object to smooth recognition results to increase accuracy.
        recognizeCommands =
                new RecognizeCommands(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

    }

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();


    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Start recording");

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        record.stop();
        record.release();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private long lastProcessingTimeMs;


    private void recognize() {

        Log.v(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[] {SAMPLE_RATE};

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            long startTime = new Date().getTime();
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
            }

            //Object[] inputArray = {floatInputBuffer, sampleRateList};
            Object[] inputArray = {floatInputBuffer};
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, outputScores);

            // Run the model.
            tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

            // Use the smoother to figure out if we've had a real recognition event.
            long currentTime = System.currentTimeMillis();
            final RecognizeCommands.RecognitionResult result =
                    recognizeCommands.processLatestResults(outputScores[0], currentTime);
            lastProcessingTimeMs = new Date().getTime() - startTime;

            // Printing the output
            Log.v(LOG_TAG, result.foundCommand);
            Log.v(LOG_TAG, String.valueOf(result.isNewCommand));
            Log.v(LOG_TAG, String.valueOf(result.score));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    result_txt.setText(result.foundCommand);
                    // Stuff that updates the UI

                }
            });

            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        Log.v(LOG_TAG, "End recognition");
    }
}
