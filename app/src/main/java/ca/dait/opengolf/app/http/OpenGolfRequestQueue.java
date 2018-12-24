package ca.dait.opengolf.app.http;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Created by darinamos on 2018-12-09.
 */

public final class OpenGolfRequestQueue {

    private static RequestQueue queue = null;

    private OpenGolfRequestQueue(){}

    public static void init(Context context){
        queue = Volley.newRequestQueue(context);
    }

    public static <T> void send(Request<T> request){
        queue.add(request);
    }
}
