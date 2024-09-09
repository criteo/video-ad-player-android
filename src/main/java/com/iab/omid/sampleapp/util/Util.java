package com.iab.omid.sampleapp.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;

import androidx.media3.exoplayer.ExoPlayer;

import com.iab.omid.library.criteo.adsession.media.InteractionType;
import com.iab.omid.library.criteo.adsession.media.MediaEvents;
import com.iab.omid.sampleapp.BuildConfig;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Util
 */

public final class Util {

    /**
     * open external browser and log the user interaction click event
     */
    public static void handleUserInteraction(Context context, MediaEvents mediaEvents) {
        String externalLink = "https://www.pandora.com/artist/fall-out-boy/mania/ALJxxPp4qfg6wvg";

        if (mediaEvents != null) {
            mediaEvents.adUserInteraction(InteractionType.CLICK);
        }
        try {
            Intent externalIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(externalLink));
            context.startActivity(externalIntent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Toggle Player mute/un mute and log the events
     */
    public static void toggleMute(MediaEvents mediaEvents, ExoPlayer player, TextView muteTextView) {
        int PLAYER_MUTE = 0;
        int PLAYER_VOLUME= 1;
        if (mediaEvents != null && player != null) {
            float currentVolume = player.getVolume();
            // player is muted, un mute it
            if (currentVolume == PLAYER_MUTE) {
                player.setVolume(PLAYER_VOLUME);
                mediaEvents.volumeChange(PLAYER_VOLUME);
                muteTextView.setText("Mute");
            } else {
                player.setVolume(PLAYER_MUTE);
                mediaEvents.volumeChange(PLAYER_MUTE);
                muteTextView.setText("Unmute");
            }
        }
    }
}
