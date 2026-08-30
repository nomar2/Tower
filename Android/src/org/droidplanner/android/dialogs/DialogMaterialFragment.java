package org.droidplanner.android.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.droidplanner.android.R;
import org.xmlpull.v1.XmlPullParser;

import timber.log.Timber;

/**
 * "What's new" dialog. Renders res/raw/changelog.xml with a self-contained parser.
 */
public class DialogMaterialFragment extends DialogFragment {

    public DialogMaterialFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final CharSequence content = buildChangelog();

        final TextView text = new TextView(getActivity());
        final int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                getResources().getDisplayMetrics());
        text.setPadding(pad, pad, pad, pad);
        text.setTextIsSelectable(true);
        text.setText(content);

        final ScrollView scroll = new ScrollView(getActivity());
        scroll.addView(text);

        return new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle)
                .setTitle("Changelog")
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                })
                .create();
    }

    private CharSequence buildChangelog() {
        final StringBuilder html = new StringBuilder();
        try {
            final XmlPullParser parser = getResources().getXml(R.xml.changelog_data);
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if ("changelogversion".equals(tag)) {
                        final String version = parser.getAttributeValue(null, "versionName");
                        final String date = parser.getAttributeValue(null, "changeDate");
                        html.append("<br/><b>").append(version == null ? "" : version).append("</b>");
                        if (date != null) html.append(" &#8211; ").append(date);
                        html.append("<br/>");
                    } else if ("changelogtext".equals(tag) || "changelogimprovement".equals(tag)) {
                        final String line = parser.nextText();
                        if (line != null && !line.trim().isEmpty()) {
                            html.append("&#8226;&#160;").append(line.trim()).append("<br/>");
                        }
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            Timber.w(e, "Unable to parse changelog");
            return "";
        }
        return Html.fromHtml(html.toString(), Html.FROM_HTML_MODE_LEGACY);
    }
}
