package ca.dait.opengolf.app.utlis;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * Created by darinamos on 2018-12-22.
 */

public class BitmapFactory {

    public static Bitmap fromDrawable(int resourceId, Resources resources){
        return fromDrawable(resources.getDrawable(resourceId));
    }

    public static Bitmap fromDrawable(Drawable drawable){
        //Drawable drawable = MapDriver.this.mapView.getResources().getDrawable(R.drawable.ic_waypoint);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
