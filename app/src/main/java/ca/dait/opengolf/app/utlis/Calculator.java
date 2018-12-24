package ca.dait.opengolf.app.utlis;

import android.content.res.Resources;

/**
 * Created by darinamos on 2018-03-11.
 */

public class Calculator {

    private static final long EARTH_CIRCUMFERENCE = 40075000;
    private static final long EARTH_BASE_PIXELS = 256;
    private static final float HOLE_BUFFER = 1.6f;
    private static final float YARDS_FACTOR = 1.093613298337708f;

    /**
     * Converts given meters to yards
     *
     * @param meters
     * @return
     */
    public static int getYards(double meters){
        return Math.round((float)meters * YARDS_FACTOR);
    }


    /**
     * This method needs to be tuned with a high precision math library.
     *
     * This algorithm is based on the fact that at zoom level 0, google maps uses 256dp to map the
     * entire circumference of the earth 40075000m.
     *
     * Formula:  256 * 2 ^ N = 256dp OR..... 256 x 2 ^ N = 40075000m/256dp
     *
     * Therefore, give a distance X, and a screen height Y, we can solve for N
     *      256 X 2 ^ N = X/Y
     *
     * This method does:
     *
     * log2(((pixels-dp * earth-circumference) / distance-meters) / 256)
     *
     * Because there is no log2() method, we use natural log: log(var)/log(2)
     *
     * @param pixels
     * @param distance
     * @return
     */
    public static float getZoom(int pixels, double distance){
        //Must convert pixels to density-independent pixels (dp)
        long dp = Math.round(pixels / Resources.getSystem().getDisplayMetrics().density);

        long zx = dp * (long)EARTH_CIRCUMFERENCE;
        float zxy = zx / ((float)distance * HOLE_BUFFER); // send a buffer to the distance so the center of the green isn't on the edge of screen
        float toLog = zxy / (float)EARTH_BASE_PIXELS;
        double zoom = Math.log(toLog) / Math.log(2);
        return (float)zoom;
    }
}
