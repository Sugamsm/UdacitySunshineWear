package com.example.android.sunshine.app.wear;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;


public class WearMessageListener extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if (messageEvent.getPath().equals("/sync-request")) {
            Log.i("Message Listener", "Got a Request for Sync...");
            startService(new Intent(this, WearSyncService.class));
        }
    }
}
