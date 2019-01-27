package ca.dait.opengolf.app.network;

import android.support.annotation.Nullable;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;

import java.io.UnsupportedEncodingException;

import ca.dait.opengolf.entities.GsonFactory;

/**
 * Created by darinamos on 2018-12-09.
 */

public class EntityRequest<T> extends JsonRequest<T> {

    private Class<T> resultClass;

    public EntityRequest(Class<T> resultClass, int method, String url,
            Response.Listener<T> listener, @Nullable Response.ErrorListener errorListener){
        super(method, url,null, listener, errorListener);
        this.resultClass = resultClass;
    }

    public EntityRequest(Class<T> resultClass, int method, String url, Object requestObj,
                 Response.Listener<T> listener, @Nullable Response.ErrorListener errorListener){
        super(method, url, GsonFactory.instance().toJson(requestObj), listener, errorListener);
        this.resultClass = resultClass;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String result = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));

            return Response.success(GsonFactory.instance().fromJson(result, this.resultClass),
                    HttpHeaderParser.parseCacheHeaders(response));
        }
        catch(UnsupportedEncodingException e){
            return Response.error(new ParseError(e));
        }
    }
}
