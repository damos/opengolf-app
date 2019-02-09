package ca.dait.opengolf.app.activities;

import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.gson.Gson;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.entities.course.Course;

/**
 * Created by darinamos on 2019-02-09.
 */

public class CourseLongPressDialog extends Dialog {
    private final MenuOverlayActivity activity;
    private final int entityId;
    private final Course course;

    CourseLongPressDialog(MenuOverlayActivity activity, Integer entityId, Course course){
        super(activity);
        this.activity = activity;
        this.entityId = entityId;
        this.course = course;

        this.setContentView(R.layout.course_long_press);

        Window window = this.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tv = this.findViewById(R.id.courseName);
        tv.setText(course.getFullName());

        View editButton = this.findViewById(R.id.editButton);
        editButton.setOnClickListener(v -> {
            this.dismiss();

            Intent output = new Intent();
            output.putExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, MenuOverlayActivity.INTENT_RESULT_EDIT_COURSE);
            output.putExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_ID, this.entityId);
            output.putExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE, new Gson().toJson(this.course));
            this.activity.setResult(MenuOverlayActivity.RESULT_OK, output);
            this.activity.finish();
        });

        View deleteButton = this.findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(v -> {
           activity.repo.delete(
                this.entityId,
                i -> {
                    this.dismiss();
                    activity.showSavedCourses();
                }
            );
        });

        View cancelButton = this.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(v -> {
            this.dismiss();
        });

    }

}
