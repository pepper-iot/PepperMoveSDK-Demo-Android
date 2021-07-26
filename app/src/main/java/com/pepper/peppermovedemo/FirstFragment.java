package com.pepper.peppermovedemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.pepper.peppermove.PepperMove;
import com.pepper.peppermove.PepperMoveVideoSession;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import static android.content.Context.AUDIO_SERVICE;

public class FirstFragment extends Fragment {
    final static String LOG_TAG = "PepperMoveDemo";

    private final Object mutex = new Object();
    private PepperMove move;
    private PepperMoveVideoSession session;
    private boolean audioEnabled = false;
    private boolean twoWayTalkEnabled = false;
    private boolean inputEnabled = true;
    private boolean videoStarted = false;
    private Button muteButton;
    private Button talkButton;
    private Button inputButton;
    private String deviceId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_first, container, false); // Inflate the layout for this fragment
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("PepperLogin", "onViewCreated");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        FrameLayout videoBackground = view.findViewById(R.id.video_background);
        videoBackground.setBackgroundColor(Color.BLACK);

        PepperMove.setLogAdapter(2, new PepperMove.LoggerAdapterHandler() {
            @Override
            public void onLog(String logMsg) {
                Log.d("PepperMove-Adapter", logMsg);
            }
        });

        // String moveURL = "wss://dev.move.pepperos.io/ws";
        String moveURL = "wss://stage.move.pepperos.io/ws";
        // String moveURL = "wss://prod.move.pepperos.io/ws";

        DemoApplication app = (DemoApplication) getActivity().getApplication();
        move = app.move;

        if (move == null) {
            move = new PepperMove(moveURL, getActivity());
            app.move = move;
        }

        new Thread(() -> {
            synchronized(mutex) {
                // Authenticate by with email & password to get a cognito token, which we then use to authenticate with Move
                if (app.email == null || app.token == null) { // Only if we haven't done it already
                    if (!authenticateWithPepper())
                        return;
                }

                // Spin up a live stream
                session = new PepperMoveVideoSession();
                session.deviceId = deviceId;

                // Now authenticate with Move
                move.setCredentials(app.email, app.token);

//                move.formatSdCard(session.deviceId, (output, exception) -> {
//                    Log.i(LOG_TAG, "formatSdCard - success: " + (exception == null));
//                });
//                move.getSdCardInfo(session.deviceId, (output, exception) -> {
//                    Log.i(LOG_TAG, "getSdCardInfo - success: " + (exception == null));
//                });
//                move.takeSnapshot(session.deviceId, (output, exception) -> {
//                    Log.i(LOG_TAG, "takeSnapshot - success: " + (exception == null));
//                });
//                move.startRecording(session.deviceId, (output, exception) -> {
//                    Log.i(LOG_TAG, "startRecording - success: " + (exception == null));
//                });
//                move.stopRecording(session.deviceId, (output, exception) -> {
//                    Log.i(LOG_TAG, "stopRecording - success: " + (exception == null));
//                });
//                move.deleteSdCardFile(session.deviceId, "20210714064101.jpg", (output, exception) -> {
//                    Log.i(LOG_TAG, "deleteSdCardFile - success: " + (exception == null));
//                });
//                move.uploadSdCardFile(session.deviceId, "20210714064101.jpg", (output, exception) -> {
//                    Log.i(LOG_TAG, "uploadSdCardFile - success: " + (exception == null));
//                });

                session.tapHandler = new PepperMoveVideoSession.TapHandler() {
                    @Override
                    public void onTap() {
                        Log.i(LOG_TAG, "Single tap on video");
                    }
                };

                session.videoCallbacks = new PepperMoveVideoSession.VideoHandlers() {
                    @Override
                    public void onVideoAdded() {
                        Log.i(LOG_TAG, "onVideoAdded");
                    }

                    @Override
                    public void onVideoRemoved() {
                        Log.i(LOG_TAG, "onVideoRemoved");
                        setAudioOutputSpeaker(false);
                    }

                    @Override
                    public void onVideoReady() {
                        Log.i(LOG_TAG, "onVideoReady");
                        setAudioOutputSpeaker(true);
                    }

                    @Override
                    public void onVideoSizeUpdated(int videoWidth, int videoHeight) {
                        Log.i(LOG_TAG, "onVideoSizeUpdated: " + videoWidth + "x" + videoHeight);
                    }
                };

                if (!app.isRequestingMicPermission) { // If the permission request dialog is showing then this fragment will be paused - we'll start the video in onResume
                    Log.i(LOG_TAG, "Starting video");
                    move.startVideo(session, videoBackground);
                    videoStarted = true;
                }
            }
        }).start();

        view.findViewById(R.id.button_first).setOnClickListener(view14 -> {
            NavHostFragment.findNavController(FirstFragment.this).navigate(R.id.action_FirstFragment_to_SecondFragment);
        });

        muteButton = view.findViewById(R.id.mute_button);
        inputButton = view.findViewById(R.id.input_button);
        talkButton = view.findViewById(R.id.talk_button);
        setButtonText();

        muteButton.setOnClickListener(view13 -> {
            audioEnabled = !audioEnabled;
            move.setMute(session, !audioEnabled);
            setButtonText();
        });

        inputButton.setOnClickListener(view1 -> {
            inputEnabled = !inputEnabled;
            move.setInputEnabled(inputEnabled);
            setButtonText();
        });

        talkButton.setOnClickListener(view12 -> {
            twoWayTalkEnabled = !twoWayTalkEnabled;
            move.setTwoWayTalk(session, twoWayTalkEnabled);
            setButtonText();
        });
    }

    private void setAudioOutputSpeaker(boolean enabled) {
        Activity activity = getActivity();
        if (activity != null) {
            AudioManager audioManager = (AudioManager)activity.getSystemService(AUDIO_SERVICE);
            audioManager.setMode(enabled ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(enabled);
        }
    }

    private boolean authenticateWithPepper() {
        String email = "danbrown@pepper.me";
        String jsonResponse = null;
        String cognitoToken = null;

        try {
            String postData = "";
            // String mAuthURL = "https://dev.api.pepperos.io/authentication/byEmail";
            String mAuthURL = "https://staging.api.pepperos.io/authentication/byEmail";
            // String mAuthURL = "https://api.pepperos.io/authentication/byEmail";

            String mPassword = "Password12345!";
            URL myURL = new URL(mAuthURL);
            HttpURLConnection urlConnection = (HttpURLConnection) myURL.openConnection();
            String userCredentials = "pepper:" + email + ":" + mPassword;

            String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
            Log.d("PepperLogin", "the auth header is " + basicAuth);
            urlConnection.setRequestProperty("authorization", basicAuth);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Content-Length", "" + postData.getBytes().length);
            urlConnection.setRequestProperty("Content-Language", "en-US");
            urlConnection.setUseCaches(false);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);

            if (urlConnection.getResponseCode() == 200) {
                InputStream inputStr = urlConnection.getInputStream();
                String encoding = urlConnection.getContentEncoding() == null ? "UTF-8" : urlConnection.getContentEncoding();
                jsonResponse = IOUtils.toString(inputStr, encoding);

                Log.d("PepperLogin", "the jwt is " + jsonResponse);
                JSONObject jwt = new JSONObject(jsonResponse);
                JSONObject pepperUser = jwt.getJSONObject("pepperUser");

                Log.d("PepperLogin", "Account id = " + pepperUser.getString("account_id"));
                JSONObject cognitoProfile = jwt.getJSONObject("cognitoProfile");

                cognitoToken = cognitoProfile.getString("Token");

                // Save the credentials to the app object
                DemoApplication app = (DemoApplication) getActivity().getApplication();
                app.email = email;
                app.token = cognitoToken;

                // ID of a camera paired to the above account
                deviceId = "7fe8ca49-5d5c-41ba-8564-0af3c5ae0159";

                return true;
            }
            else
                Log.e("PepperLogin", "Authentication failed! (Status code: " + urlConnection.getResponseCode() + ")");
        }
        catch (Exception e) {
            Log.e("PepperLogin", e.toString(), e);
        }
        return false;
    }

    private void setButtonText() {
        muteButton.setText((audioEnabled ? "Disable" : "Enable") + " audio");
        inputButton.setText((inputEnabled ? "Disable" : "Enable") + " user input");
        talkButton.setText((twoWayTalkEnabled ? "Disable" : "Enable") + " talk");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(LOG_TAG, "onPause() - Stopping video");
        move.stopVideo(session);
        videoStarted = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(LOG_TAG, "onResume()");

        synchronized(mutex) {
            if (session != null && !videoStarted && !((DemoApplication)getActivity().getApplication()).isRequestingMicPermission) {
                Log.i(LOG_TAG, "Restarting video");
                move.startVideo(session, getView().findViewById(R.id.video_background));
                videoStarted = true;
            }
        }
    }
}