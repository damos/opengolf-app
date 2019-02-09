package ca.dait.opengolf.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.network.EntityRequest;
import ca.dait.opengolf.app.network.RequestQueueManager;
import ca.dait.opengolf.app.utlis.JsonRepository;
import ca.dait.opengolf.entities.course.Course;
import ca.dait.opengolf.entities.course.CourseSearchResult;

public class MenuOverlayActivity extends FragmentActivity {

    public final static String INTENT_EXTRA_RESULT = "result";
    public final static String INTENT_EXTRA_COURSE = "course";
    public final static String INTENT_EXTRA_COURSE_ID = "courseId";
    public final static String INTENT_EXTRA_COURSE_TYPE = "typeId";
    public final static String INTENT_EXTRA_LOCATION = "Location";

    public final static int INTENT_RESULT_FREE_ROAM = 1;
    public final static int INTENT_RESULT_CREATE_COURSE = 2;
    public final static int INTENT_RESULT_EDIT_COURSE = 3;
    public final static int INTENT_RESULT_PLAY_COURSE = 4;

    protected LatLng position;

    protected RequestQueue requestQueue;
    protected JsonRepository repo;

    protected EditText searchBox;
    protected View courseListHeader;
    protected View courseListEmptyView;
    protected ListView courseListView;
    protected SwipeRefreshLayout refreshLayout;

    protected RadioGroup radioGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_overlay);
        this.position = this.getIntent().getParcelableExtra(INTENT_EXTRA_LOCATION);

        this.requestQueue = RequestQueueManager.getInstance(this.getApplicationContext());
        this.repo = JsonRepository.getInstance(this.getApplicationContext());

        this.searchBox = this.findViewById(R.id.searchBox);
        this.searchBox.setOnFocusChangeListener((View view, boolean hasFocus) ->{
            if(!hasFocus){
                InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(inputManager != null) {
                    inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        this.searchBox.setOnEditorActionListener((view, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_SEARCH){
                if(this.searchBox.getText().toString().trim().length() > 0){
                    this.showSearchResults();
                    return false;
                }
                else{
                    TranslateAnimation shake = new TranslateAnimation(-5,5, 0, 0);
                    shake.setDuration(300);
                    shake.setInterpolator(new CycleInterpolator(3));
                    this.searchBox.startAnimation(shake);
                    return true;
                }
            }
            return false;
        });

        this.courseListView = this.findViewById(R.id.courseListView);
        this.courseListEmptyView = this.findViewById(R.id.noResults);

        this.radioGroup = this.findViewById(R.id.radioGroup);
        this.radioGroup.setOnCheckedChangeListener((group, checkedId) ->{
            switch(checkedId){
                case R.id.radioButtonMyCourses:
                    this.showSavedCourses();
                    break;
                case R.id.radioButtonNearMe:
                    this.showNearMe();
                    break;
            }
        });


        this.refreshLayout = this.findViewById(R.id.swipeRefreshLayout);
        this.refreshLayout.setColorSchemeColors(this.getColor(R.color.colorPrimary),
                                                this.getColor(R.color.colorPrimaryDark));
        this.refreshLayout.setOnRefreshListener(() ->{
            switch(this.radioGroup.getCheckedRadioButtonId()){
                case R.id.radioButtonMyCourses:
                    this.refreshLayout.setRefreshing(false); //No need to swipe to refresh local storage
                    break;
                case R.id.radioButtonNearMe:
                    this.showNearMe();
                    break;
                default:
                    this.showSearchResults();
            }
        });

        this.courseListHeader = this.findViewById(R.id.createCourseDefinition);
        this.courseListHeader.setOnClickListener(view -> {
            Intent output = new Intent();
            output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_CREATE_COURSE);
            this.setResult(RESULT_OK, output);
            this.finish();
        });

        this.findViewById(R.id.buttonFreeRoam).setOnClickListener(view -> {
            Intent output = new Intent();
            output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_FREE_ROAM);
            this.setResult(RESULT_OK, output);
            this.finish();
        });

        this.showSavedCourses();
    }

    protected void showSavedCourses(){
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        this.refreshLayout.setRefreshing(true);

        this.courseListEmptyView.setVisibility(View.GONE);

        this.repo.getAll(Course.class, JsonRepository.TYPE_COURSE, results ->{

            if(results.size() == 0){
                this.refreshLayout.setRefreshing(false);
                this.courseListView.setAdapter(new CourseListAdapter(false, true));
                return;
            }

            //A single google place ID could have multiple golf courses.
            //Build on-to-many map of placeId to courses.
            Map<String, List<JsonRepository.JsonEntity<Course>>> placeIdToCourses = new HashMap<>();
            results.forEach(entity ->{
                placeIdToCourses.computeIfAbsent(entity.ref.getGooglePlaceId(),
                        (key) -> new ArrayList<>()).add(entity);
            });

            //Fetch Place data.
            //TODO: Leverage caching.
            GeoDataClient client = Places.getGeoDataClient(this);
            client.getPlaceById(placeIdToCourses.keySet().toArray(new String[placeIdToCourses.size()]))
                    .addOnCompleteListener(task ->{
                        PlaceBufferResponse response = task.getResult();
                        response.forEach((Place place) -> {
                            Double distance = (this.position == null) ? null :
                                    SphericalUtil.computeDistanceBetween(this.position, place.getLatLng());
                            String address[] = place.getAddress().toString().split(",");
                            List<JsonRepository.JsonEntity<Course>> entities = placeIdToCourses.get(place.getId());
                            entities.forEach(entity -> {
                                entity.ref.setMunicipality(address[1]);
                                entity.ref.setCountry(address[3]);
                                entity.ref.setFacilityName(place.getName().toString());
                                entity.ref.setState(address[2].substring(1,3));
                                entity.ref.setDistance(distance);
                            });
                        });

                        List<JsonRepository.JsonEntity<Course>> finalCourses = new ArrayList<>();
                        placeIdToCourses.values().forEach(finalCourses::addAll);
                        if(this.position == null){
                            finalCourses.sort(Comparator.comparing((entity) -> entity.ref.getFacilityName()));
                        }
                        else{
                            finalCourses.sort(Comparator.comparing((entity) -> entity.ref.getDistance()));
                        }
                        this.refreshLayout.setRefreshing(false);
                        this.courseListView.setAdapter(new CourseListAdapter(
                                finalCourses,false, true
                        ));
                    });
        });
    }

    protected void showSearchResults(){
        this.radioGroup.clearCheck();
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        String searchText = this.searchBox.getText().toString();
        String url = (this.position == null) ?
                String.format(this.getString(R.string.url_searchTerm), searchText) :
                String.format(this.getString(R.string.url_searchTermWithLoc), searchText, this.position.latitude, this.position.longitude);
        this.searchRemoteCourses(url);
    }

    protected void showNearMe(){
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        String url = (this.position == null) ?
                this.getString(R.string.url_search) :
                String.format(this.getString(R.string.url_searchByLoc), this.position.latitude, this.position.longitude);
        this.searchRemoteCourses(url);
    }

    protected void searchRemoteCourses(String url){
        this.refreshLayout.setRefreshing(true);

        EntityRequest<CourseSearchResult> request =
            new EntityRequest<>(CourseSearchResult.class, Request.Method.GET, url,
                response ->{
                    this.courseListView.setAdapter(
                            new CourseListAdapter(response.getResults(), true, false));
                    this.refreshLayout.setRefreshing(false);
                },
                error ->{
                    this.refreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Unable to find nearby courses.", Toast.LENGTH_SHORT).show();
                }
            );

        this.requestQueue.add(request);
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();
        this.overridePendingTransition(0,0);
    }

    private class CourseListAdapter<T> extends BaseAdapter{
        private static final String LOCALE = "%s, %s, %s";
        private static final String LOCALE_WITH_DISTANCE = "%s, %s, %s - %s";

        private final String type;
        private final String ids[];
        private final Course courses[];
        private boolean longPressEdit = false;

        CourseListAdapter(@NonNull Course courses[], boolean showEmptyView, boolean showHeader){
            this.type = "remote";
            this.courses = courses;
            this.ids = Arrays.stream(courses)
                    .map(Course::getRemoteId)
                    .toArray(String[]::new);
            this.setViews(showEmptyView, showHeader);
        }

        CourseListAdapter(boolean showEmptyView, boolean showHeader){
            this(new Course[]{}, showEmptyView, showHeader);
        }

        CourseListAdapter(@NonNull List<JsonRepository.JsonEntity<Course>> courses,
                          boolean showEmptyView, boolean showHeader){
            this.type = "saved";
            this.courses = courses.stream()
                    .map(entity -> entity.ref)
                    .toArray(Course[]::new);
            this.ids = courses.stream()
                    .map(entity -> String.valueOf(entity.id))
                    .toArray(String[]::new);
            this.setViews(showEmptyView, showHeader);
            this.longPressEdit = true;
        }

        private void setViews(boolean showEmptyView, boolean showHeader) {
            MenuOverlayActivity.this.courseListEmptyView.setVisibility(
                    (showEmptyView && courses.length == 0) ? View.VISIBLE : View.GONE);
            MenuOverlayActivity.this.courseListHeader.setVisibility(
                    showHeader ? View.VISIBLE : View.GONE);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup viewGroup) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.list_row_course, viewGroup, false);
            }
            convertView.setTag(position);
            TextView primaryTextView = convertView.findViewById(R.id.courseName);
            TextView altTextView = convertView.findViewById(R.id.courseCountry);

            primaryTextView.setText(this.courses[position].getFullName());
            Double distanceInM = this.courses[position].getDistance();
            altTextView.setText(
                    (distanceInM == null) ?
                            String.format(LOCALE, this.courses[position].getMunicipality(),
                                    this.courses[position].getState(),this.courses[position].getCountry()) :
                            String.format(LOCALE_WITH_DISTANCE, this.courses[position].getMunicipality(),
                                    this.courses[position].getState(),this.courses[position].getCountry(),
                                    MenuOverlayActivity.getDistanceInKm(distanceInM) + "km"));

            convertView.setOnClickListener(view -> {
                Integer index = (Integer)view.getTag();
                Course course = this.courses[index];
                Intent output = new Intent();
                output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_PLAY_COURSE);
                output.putExtra(INTENT_EXTRA_COURSE_ID, this.ids[index]);
                output.putExtra(INTENT_EXTRA_COURSE_TYPE, this.type);
                output.putExtra(INTENT_EXTRA_COURSE, new Gson().toJson(course));
                MenuOverlayActivity.this.setResult(RESULT_OK, output);
                MenuOverlayActivity.this.finish();
            });

            if(this.longPressEdit) {
                convertView.setOnLongClickListener(view -> {
                    Integer index = (Integer) view.getTag();
                    CourseLongPressDialog dialog = new CourseLongPressDialog(MenuOverlayActivity.this,
                            Integer.valueOf(this.ids[index]), this.courses[index]);
                    dialog.show();
                    return true;
                });
            }

            return convertView;
        }

        @Override
        public long getItemId(int position){
            return 0;
        }

        @Override
        public Course getItem(int position){
            return this.courses[position];
        }

        @Override
        public int getCount() {
            return this.courses.length;
        }
    }

    //Converts double in Meters to Km to 1 decimal.
    private static double getDistanceInKm(double distanceInM){
        return Math.round(distanceInM / 100) / 10d;
    }
}
