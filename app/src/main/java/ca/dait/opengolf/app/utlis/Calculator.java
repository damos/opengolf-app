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

    public static int getMeters(double yards){
        return Math.round((float)yards / YARDS_FACTOR);
    }

    public static double getDistanceInKm(double distanceInM){
        return Math.round(distanceInM / 100) / 10d;
    }

    public static float getPixelsFromDp(float dp){
        return dp * Resources.getSystem().getDisplayMetrics().density;
    }

    /**
     * TODO: This method should be tuned for better precision but seems to work great for now.
     *
     * This algorithm is based on the fact that at zoom level 0, google maps uses 256dp
     * (density independent pixels) to map the entire circumference of the earth 40075000m. For each
     * additional whole number zoom level, the # of dp's doubles.
     *
     *  256 * 2 ^ N = # of dp for earth's circumference
     *
     *  Therefore:
     *
     *  40075000m / (256 * 2 ^ N) = # of meters per single dp
     *
     *  Therefore, if we know the screen height in dp:
     *
     *  screenHeight * (40075000m / (256 * 2 ^ N)) = # of meters represented in the screen height.
     *
     *  Therefore if we also know the screen height we want to fit into the screen we can solve
     *  for N.
     *
     *  Finally, given screen height X (dp) and distance Y (m), solve for N:
     *
     *  -> X * (40075000m / (256 * 2 ^ N)) = Y
     *  -> 40075000m / (256 * 2 ^ N) = Y/X
     *  (cross multiply)
     *  -> Y * (256 * 2 ^ N) = X * 40075000m
     *  -> 256 * 2 ^ N = (X * 40075000m) / Y
     *  -> 2 ^ N = ((X * 40075000m) / Y) / 256
     *
     *  -> N = log2((X * 40075000m) / Y) / 256)
     *
     * Therefore this method does:
     *
     * log2(((pixels-dp * earth-circumference) / distance-meters) / 256)
     *
     * Because there is no log2() method, we use natural log: log(target)/log(2)
     *
     * @param pixels
     * @param distance
     * @return
     */
    public static float getZoom(int pixels, double distance){
        //Must convert pixels to density-independent pixels (dp)
        long dp = Math.round(pixels / Resources.getSystem().getDisplayMetrics().density);

        long zx = dp * EARTH_CIRCUMFERENCE;
        float zxy = zx / ((float)distance * HOLE_BUFFER); // send a buffer to add some padding
        float toLog = zxy / (float)EARTH_BASE_PIXELS;
        double zoom = Math.log(toLog) / Math.log(2);
        return (float)zoom;
    }
}
