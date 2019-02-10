package ca.dait.opengolf.app.drivers;

import android.content.Intent;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.gson.Gson;

import java.util.Arrays;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.activities.MenuOverlayActivity;
import ca.dait.opengolf.entities.course.Course;

/**
 * Created by darinamos on 2019-01-19.
 */

public class PlayCourseMapDriver extends AbstractTrackingMapDriver {

    private final long courseId;
    private final String remoteCourseId;
    private final Course course;
    private int currentHoleNo = 0;
    private Flag[] courseFlags;

    public PlayCourseMapDriver(MainActivity mainActivity, GoogleMap googleMap, Intent intent){
        super(mainActivity, googleMap);

        this.courseId = intent.getLongExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_ID, -1);
        this.remoteCourseId = intent.getStringExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_REMOTE_ID);
        String rawCourse = intent.getStringExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE);
        this.course = new Gson().fromJson(rawCourse, Course.class);

        this.showText(Panel.COURSE_TITLE, course.getFullName());
        this.showPreview();
        this.showRefreshing(true);
    }

    public void showPreview(){

        final LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();
        this.courseFlags = Arrays.stream(this.course.getHoles())
                .map(hole ->{
                    LatLng position = new LatLng(hole.getLat(), hole.getLon());
                    boundsBuilder.include(position);
                    return new Flag(this.googleMap, position, false);
                }).toArray(Flag[]::new);

        this.googleMap.moveCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(),
                this.mainActivity.getResources().getInteger(R.integer.coursePreviewPadding))
        );

        this.googleMap.setOnMapLoadedCallback(() ->{
            this.googleMap.setOnMapLoadedCallback(null);
            this.showRefreshing(false);

            this.setClickListener(Button.START, view -> {
                this.hide(Button.START);
                this.hide(Panel.COURSE_TITLE);
                for(Flag flag : this.courseFlags){
                    flag.hide();
                }

                this.goToHole(0);
                this.start();
            });
            this.show(Button.START);
        });
    }

    @Override
    public boolean canRestart(Intent intent) {
        long courseId = intent.getLongExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_ID, -1);
        String remoteId = intent.getStringExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_REMOTE_ID);

        return (intent.getIntExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, -1) == MenuOverlayActivity.INTENT_RESULT_PLAY_COURSE) &&
                ((courseId == -1 && remoteId != null && remoteId.equals(this.remoteCourseId)) ||
                (courseId != -1 && courseId == this.courseId));
    }

    @Override
    protected void onReady() {
        super.onReady();

        this.googleMap.setOnMapClickListener((position) -> {
            if (this.waypoint == null) {
                this.waypoint = new Waypoint(position);
            } else {
                this.waypoint.updatePosition(position);
            }
            this.show(Button.WAYPOINT, Button.CANCEL);
            this.updatePanel();
        });

        this.setClickListener(Button.PREVIOUS, view -> {
            this.goToHole(this.currentHoleNo - 1);
        });

        this.setClickListener(Button.NEXT, view -> {
            this.goToHole(this.currentHoleNo + 1);
        });
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setZoomGesturesEnabled(true);
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
        this.hide(Button.CANCEL);
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
            this.hide(Button.CANCEL);
        }
    }

    private void goToHole(int newHoleNo){
        this.courseFlags[this.currentHoleNo].hide();
        this.courseFlags[newHoleNo].show();
        this.currentHoleNo = newHoleNo;
        super.flag = this.courseFlags[this.currentHoleNo];

        this.setHoleNo(newHoleNo + 1);

        if(newHoleNo == 0 && this.courseFlags.length <= 1){
            this.hide(Button.NEXT, Button.PREVIOUS);
        }
        else if(newHoleNo == 0){
            this.show(Button.NEXT);
            this.hide(Button.PREVIOUS);
        }
        else if(newHoleNo >= this.course.getHoles().length - 1){
            this.show(Button.PREVIOUS);
            this.hide(Button.NEXT);
        }
        else{
            this.show(Button.NEXT, Button.PREVIOUS);
        }

        //Always reset the zoom and map when holes change
        this.cancelUserAction();
        this.updatePanel();
        this.positionCamera();
    }

}
