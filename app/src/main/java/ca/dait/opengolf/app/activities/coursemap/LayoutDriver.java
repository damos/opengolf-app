package ca.dait.opengolf.app.activities.coursemap;

import android.view.View;
import android.widget.TextView;

import ca.dait.opengolf.app.R;

/**
 * This class hides the details of the layout from the Map Driver. The Map Driver only
 * needs to be concerned with the "State" of the UI and not the elements themselves.
 *
 * Created by darinamos on 2018-03-14.
 */

public class LayoutDriver {
    private final TextView holeNo;
    private final View startButton;
    private final View prevButton;
    private final View nextButton;
    private final View cancelButton;
    private final View waypointButton;
    private final TextView curLocToGreen;
    private final TextView pointToGreen;
    private final TextView curLocToPoint;

    LayoutDriver(CourseMapActivity activity){
        this.holeNo = activity.findViewById(R.id.holeNo);
        this.startButton = activity.findViewById(R.id.startButton);
        this.prevButton = activity.findViewById(R.id.prevButton);
        this.nextButton = activity.findViewById(R.id.nextButton);
        this.cancelButton = activity.findViewById(R.id.cancelButton);
        this.waypointButton = activity.findViewById(R.id.waypointButton);
        this.curLocToGreen = activity.findViewById(R.id.curLocToGreen);
        this.pointToGreen = activity.findViewById(R.id.pointToGreen);
        this.curLocToPoint = activity.findViewById(R.id.curLocToPoint);
    }

    public void setPreviousListener(View.OnClickListener listener){
        this.prevButton.setOnClickListener(listener);
    }

    public void setNextListener(View.OnClickListener listener){
        this.nextButton.setOnClickListener(listener);
    }

    public void setCancelListener(View.OnClickListener listener){
        this.cancelButton.setOnClickListener(listener);
    }

    public void setWaypointButtonListener(View.OnClickListener listener){
        this.waypointButton.setOnClickListener(listener);
    }

    public void setStartButtonListener(View.OnClickListener listener){
        this.startButton.setOnClickListener(listener);
    }

    public void start(){
        this.curLocToGreen.setVisibility(View.VISIBLE);
        this.holeNo.setVisibility(View.VISIBLE);
        this.startButton.setVisibility(View.GONE);
    }

    public void showWayPointAction(){
        this.waypointButton.setVisibility(View.VISIBLE);
    }

    public void hideWayPointAction(){
        this.waypointButton.setVisibility(View.GONE);
    }

    public void showCancelable(){
        this.cancelButton.setVisibility(View.VISIBLE);
    }

    public void clearCancelable(){
        this.cancelButton.setVisibility(View.GONE);
    }

    public void setGreenDistance(int distance, int accuracy){
        if(accuracy > 1){
            this.curLocToGreen.setText(distance + "±" + accuracy + "y");
        }
        else{
            this.curLocToGreen.setText(distance + "y");
        }
    }

    public void setHoleNo(int holeNo){
        this.holeNo.setText("#" + holeNo);
    }

    public enum NavState{
        BEGINNING(View.GONE, View.VISIBLE),
        MIDDLE(View.VISIBLE, View.VISIBLE),
        END(View.VISIBLE, View.GONE);

        private final int prevVisibility;
        private final int nextVisibility;
        private NavState(int prevVisibility, int nextVisibility){
            this.prevVisibility = prevVisibility;
            this.nextVisibility = nextVisibility;
        }
    }

    public void setNavState(NavState navState){
        this.prevButton.setVisibility(navState.prevVisibility);
        this.nextButton.setVisibility(navState.nextVisibility);
    }

    public void setWayPoint(int pointToGreen, int locToPoint){
        this.pointToGreen.setText("▲ " + pointToGreen + "y");
        this.curLocToPoint.setText("▼ " + locToPoint + "y");
        this.pointToGreen.setVisibility(View.VISIBLE);
        this.curLocToPoint.setVisibility(View.VISIBLE);
    }

    public void clearWayPoint(){
        this.pointToGreen.setVisibility(View.GONE);
        this.curLocToPoint.setVisibility(View.GONE);
        this.pointToGreen.setText("");
        this.curLocToPoint.setText("");
    }
}
