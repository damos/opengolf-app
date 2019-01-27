package ca.dait.opengolf.app.network;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Maintains a singleton instance of the Application RequestQueue.
 */
public final class RequestQueueManager {

    private static RequestQueue instance = null;

    private RequestQueueManager(){}
    public static RequestQueue getInstance(Context applicationContext){
        if(instance == null){
            instance = Volley.newRequestQueue(applicationContext);
        }
        return instance;
    }
}
