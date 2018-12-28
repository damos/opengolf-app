package ca.dait.opengolf.app.activities.mainmenu;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.coursemap.CourseMapActivity;
import ca.dait.opengolf.app.gps.LocationService;
import ca.dait.opengolf.app.http.EntityRequest;
import ca.dait.opengolf.app.http.OpenGolfRequestQueue;
import ca.dait.opengolf.entities.course.Course;
import ca.dait.opengolf.entities.course.CourseSearchResult;

public class MainMenuActivity extends AppCompatActivity {

    private LocationService locationService;
    private SwipeRefreshLayout refreshLayout;
    private EditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState){
        super.onPostCreate(savedInstanceState);

        //First thing the app does.
        OpenGolfRequestQueue.init(this.getApplicationContext());

        this.refreshLayout = this.findViewById(R.id.swipeRefresh);
        this.refreshLayout.setColorSchemeColors(this.getResources().getColor(R.color.colorPrimaryDark));
        this.refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                MainMenuActivity.this.showCourses();
            }
        });

        View locateButton = this.findViewById(R.id.locateButton);
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainMenuActivity.this.searchBox.setText("");
                MainMenuActivity.this.showCourses();
            }
        });

        this.searchBox = this.findViewById(R.id.searchBox);
        this.searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_SEARCH){
                    if(MainMenuActivity.this.searchBox.getText().toString().trim().length() > 0){
                        MainMenuActivity.this.showCourses();
                        return false;
                    }
                    else{
                        TranslateAnimation shake = new TranslateAnimation(-5,5, 0, 0);
                        shake.setDuration(300);
                        shake.setInterpolator(new CycleInterpolator(3));
                        MainMenuActivity.this.searchBox.startAnimation(shake);
                        return true;
                    }
                }
                return false;
            }
        });

        this.showCourses();
    }

    private void showCourses(){
        View view = this.getCurrentFocus();
        if(view != null) {
            view.clearFocus();
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }

        this.refreshLayout.setRefreshing(true);

        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setNumUpdates(1);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        // TODO: User could take a while to respond to the promt.. might have to re-consider how the LocationService is coded
        //locationRequest.setExpirationDuration(30000);

        this.locationService = new LocationService(this, locationRequest, new LocationCallback(){
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();

                String urlTemplate = MainMenuActivity.this.getString(R.string.url_search);
                String url = String.format(urlTemplate, location.getLatitude(), location.getLongitude(),
                        MainMenuActivity.this.searchBox.getText());

                EntityRequest<CourseSearchResult> request =
                        new EntityRequest<>(CourseSearchResult.class, Request.Method.GET, url,

                                new Response.Listener<CourseSearchResult>() {
                                    @Override
                                    public void onResponse(CourseSearchResult response) {
                                        if(response.getResults().length > 0) {
                                            ListView listView = MainMenuActivity.this.findViewById(R.id.courseListView);
                                            listView.setAdapter(MainMenuActivity.this.new CourseListAdapter(response));
                                        }
                                        else{
                                            ListView listView = MainMenuActivity.this.findViewById(R.id.courseListView);
                                            listView.setAdapter(MainMenuActivity.this.new NoResultsListAdapter());
                                        }
                                        MainMenuActivity.this.refreshLayout.setRefreshing(false);
                                    }
                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        Toast.makeText(MainMenuActivity.this, "Unable to find nearby courses.",
                                                Toast.LENGTH_SHORT).show();

                                        MainMenuActivity.this.refreshLayout.setRefreshing(false);
                                    }
                                }
                        );

                OpenGolfRequestQueue.send(request);
            }
        });
        this.locationService.start();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(LocationService.checkPermissions(requestCode, permissions, grantResults)){
            //Re-try hideStartButton after user accepts permissions.
            this.locationService.start();
        }
        else{
            LocationService.noPermissionsFinish(this);
        }
    }

    private class NoResultsListAdapter extends BaseAdapter{

        @Override
        public int getCount() { return 1;}

        @Override
        public Object getItem(int i) { return null; }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            return getLayoutInflater().inflate(R.layout.course_list_noresults, null);
        }
    }

    private class CourseListAdapter extends BaseAdapter{

        private final CourseSearchResult searchResults;

        private CourseListAdapter(CourseSearchResult searchResults){
            this.searchResults = searchResults;
        }

        @Override
        public int getCount() {
            return searchResults.getResults().length;
        }

        @Override
        public Course getItem(int i) {
            return this.searchResults.getResults()[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            Course course = searchResults.getResults()[i];

            view = getLayoutInflater().inflate(R.layout.course_list_row, null);
            view.setTag(R.string.courseId, course.getId());

            TextView tv = view.findViewById(R.id.courseName);
            tv.setText(course.getDetails().getName());

            Double distanceInM = course.getDistance();
            String altText = (distanceInM == null) ? course.getDetails().getCountry() :
                    course.getDetails().getCountry() + " - " + MainMenuActivity.getDistanceInKm(distanceInM) + "km";

            tv = view.findViewById(R.id.courseCountry);
            tv.setText(altText);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view){
                    Intent intent = new Intent(MainMenuActivity.this, CourseMapActivity.class);
                    intent.putExtra(view.getResources().getString(R.string.courseId), (String)view.getTag(R.string.courseId));
                    startActivity(intent);
                }
            });

            return view;
        }
    }

    //Converts double in Meters to Km to 1 decimal.
    private static double getDistanceInKm(double distanceInM){
        return Math.round(distanceInM / 100) / 10d;
    }
}
