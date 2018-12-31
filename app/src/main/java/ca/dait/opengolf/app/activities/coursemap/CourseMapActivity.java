package ca.dait.opengolf.app.activities.coursemap;

import android.os.Bundle;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.http.EntityRequest;
import ca.dait.opengolf.app.http.OpenGolfRequestQueue;
import ca.dait.opengolf.entities.course.CourseDetails;

public class CourseMapActivity extends TrackingMapActivity{

    private CourseDetails courseDetails;

    private int currentHoleNo = 0;
    private Marker[] courseMarkers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String courseId = this.getIntent().getStringExtra(this.getString(R.string.courseId));
        String url = String.format(this.getString(R.string.url_getCourse), courseId);

        EntityRequest<CourseDetails> request =
            new EntityRequest<>(CourseDetails.class, Request.Method.GET, url, (courseDetails) -> {
                this.courseDetails = courseDetails;
                this.layoutDriver.setCourseTitle(this.courseDetails.getName());

                SupportMapFragment mapFragment = (SupportMapFragment)
                            this.getSupportFragmentManager().findFragmentById(R.id.courseMap);
                    mapFragment.getMapAsync(CourseMapActivity.this);
                },
                (error) ->{
                    Toast.makeText(CourseMapActivity.this, "Unable to load course.", Toast.LENGTH_SHORT).show();
                    this.finish();
                });

        OpenGolfRequestQueue.send(request);

        this.layoutDriver.showStartButton();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can send markers or lines, send listeners or move the camera. In this case,
     * we just send a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        super.onMapReady(googleMap);
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setRotateGesturesEnabled(false);
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setZoomGesturesEnabled(false);

        //Draw Preview
        CourseDetails.Hole holes[] = this.courseDetails.getHoles();
        this.courseMarkers = new Marker[holes.length];
        final LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();

        for(int i = 0; i < holes.length; i++){
            LatLng pos = new LatLng(holes[i].getLat(), holes[i].getLon());
            boundsBuilder.include(pos);

            this.courseMarkers[i] = this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                            .anchor(this.anchorFlagIconX, this.anchorFlagIconY)
            );
        }

        this.googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(boundsBuilder.build().getCenter(),
                    Float.valueOf(this.getString(R.string.coursePreviewZoom)))
        );

        this.layoutDriver.setStartButtonListener((view) -> {
            this.start();
        });
    }

    @Override
    protected void setInteractiveListeners() {
        super.setInteractiveListeners();

        this.googleMap.setOnMapClickListener((position) -> {
            if (this.waypoint == null) {
                this.waypoint = new Waypoint(position);
            } else {
                this.waypoint.updatePosition(position);
            }
            this.layoutDriver.showWayPointAction();
            this.layoutDriver.showCancelable();
            this.updatePanel();
        });

        this.layoutDriver.setPreviousListener((view) -> {
            this.goToHole(this.currentHoleNo - 1);
        });

        this.layoutDriver.setNextListener((view) -> {
            this.goToHole(this.currentHoleNo + 1);
        });
    }

    private void start(){
        this.layoutDriver.clearCourseTitle();
        this.layoutDriver.hideStartButton();
        this.layoutDriver.showSpinner();
        for(Marker m : this.courseMarkers){
            m.setVisible(false);
        }
        this.goToHole(0);
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setZoomGesturesEnabled(true);

        this.startGpsUpdates();
    }

    @Override
    protected void userAction(){
        super.userAction();

        //Enable extra map controls
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setRotateGesturesEnabled(true);
    }

    protected void cancelUserAction(){
        super.cancelUserAction();
        this.layoutDriver.clearCancelable();
        this.clearWayPoint();

        //Disable extra map controls
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setRotateGesturesEnabled(false);
    }

    @Override
    protected void clearWayPoint() {
        super.clearWayPoint();
        if(!this.cameraOverride){
            this.layoutDriver.clearCancelable();
        }
    }

    private void goToHole(int newHoleNo){
        this.courseMarkers[this.currentHoleNo].setVisible(false);
        this.courseMarkers[newHoleNo].setVisible(true);
        this.currentHoleNo = newHoleNo;
        super.flag = this.courseMarkers[this.currentHoleNo];

        this.layoutDriver.setHoleNo(newHoleNo + 1);

        if(newHoleNo <= 0){
            this.layoutDriver.setNavState(LayoutDriver.NavState.BEGINNING);
        }
        else if(newHoleNo >= this.courseDetails.getHoles().length - 1){
            this.layoutDriver.setNavState(LayoutDriver.NavState.END);
        }
        else{
            this.layoutDriver.setNavState(LayoutDriver.NavState.MIDDLE);
        }

        //Always reset the zoom and map when holes change
        this.cancelUserAction();
        this.updatePanel();
        this.positionCamera();
    }

}
