package com.pepper.peppermovedemo;

import android.app.Application;

import com.pepper.peppermove.PepperMove;

public class DemoApplication extends Application {
    public PepperMove move;
    public String email;
    public String token;
    public boolean isRequestingMicPermission;
}