package ca.dait.opengolf.app.drivers;

import android.content.Intent;
import android.graphics.Point;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.Marker;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.utlis.Calculator;

/**
 * Root driver to define common map driv
 */
public abstract class AbstractInteractiveMapDriver extends AbstractLayoutDriver {

    protected final GoogleMap googleMap;

    protected final float waypointLinePx;
    protected final float anchorFlagIconX;
    protected final float anchorFlagIconY;
    protected final float defaultZoom;

    public AbstractInteractiveMapDriver(MainActivity mainActivity, GoogleMap googleMap){
        super(mainActivity);
        this.googleMap = googleMap;
        this.googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        //by default disable all UI Settings
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setMapToolbarEnabled(false);
        uiSettings.setCompassEnabled(false);
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setZoomControlsEnabled(false);
        uiSettings.setIndoorLevelPickerEnabled(false);
        uiSettings.setAllGesturesEnabled(false);

        this.anchorFlagIconX = Float.valueOf(this.mainActivity.getString(R.string.anchorFlagIconX));
        this.anchorFlagIconY = Float.valueOf(this.mainActivity.getString(R.string.anchorFlagIconY));
        this.defaultZoom = Float.valueOf(this.mainActivity.getString(R.string.zoomDefault));
        this.waypointLinePx = Calculator.getPixelsFromDp(Float.valueOf(this.mainActivity.getString(R.string.waypointLineDp)));
    }

    public abstract boolean canRestart(Intent intent);

    public void pause(){}
    public void resume(){}

    public void stop() {
        this.pause();
        this.saveState();
        this.hideAll();
    }

    public void restart() {
        this.resume();
        this.restoreState();
    }

    @Override
    public void clear(){
        super.clear();
        this.googleMap.clear();
        this.googleMap.setOnMapLoadedCallback(null);
        this.googleMap.setOnMapClickListener(null);
        this.googleMap.setOnMarkerClickListener(marker -> true);//Do nothing and mark as action consumed
    }

    protected interface MarkerDragAction{
        void doAction(Marker marker);
    }

    protected class VerticalShiftMarkerDragListener implements GoogleMap.OnMarkerDragListener{
        private final int dragShiftY = AbstractInteractiveMapDriver.this.mainActivity.getResources().getInteger(R.integer.dragShiftY);
        private final MarkerDragAction action;

        VerticalShiftMarkerDragListener(){
            this.action = marker -> {};
        }

        VerticalShiftMarkerDragListener(MarkerDragAction action){
            this.action = action;
        }

        @Override
        public void onMarkerDragStart(Marker marker) {
            this.action.doAction(this.shift(marker));
        }

        @Override
        public void onMarkerDrag(Marker marker) {
            this.action.doAction(this.shift(marker));
        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            this.action.doAction(this.shift(marker));
        }

        private Marker shift(Marker marker){
            Projection projection = AbstractInteractiveMapDriver.this.googleMap.getProjection();
            Point screenPoint = projection.toScreenLocation(marker.getPosition());
            screenPoint.y += this.dragShiftY;
            marker.setPosition(projection.fromScreenLocation(screenPoint));
            return marker;
        }

    }
}
