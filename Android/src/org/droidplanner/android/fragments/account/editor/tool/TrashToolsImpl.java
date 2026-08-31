package org.droidplanner.android.fragments.account.editor.tool;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.o3dr.android.client.Drone;

import org.droidplanner.android.R;
import org.droidplanner.android.dialogs.SupportYesNoDialog;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;

import java.util.List;

/**
 * Created by Fredia Huya-Kouadio on 8/25/15.
 */
class TrashToolsImpl extends EditorToolsImpl implements View.OnClickListener {

    private static final String CLEAR_SELECTED_DIALOG_TAG = "clearSelectedWaypoints";
    private static final String CLEAR_MISSION_DIALOG_TAG = "clearMission";
    private static final String CLEAR_VEHICLE_DIALOG_TAG = "clearVehicleMission";

    TrashToolsImpl(EditorToolsFragment fragment) {
        super(fragment);
    }

    @Override
    public void onListItemClick(MissionItemProxy item) {
        if (missionProxy == null)
            return;


        missionProxy.selection.clearSelection();
        missionProxy.removeItem(item);

        if (missionProxy.getItems().size() <= 0) {
            editorToolsFragment.setTool(EditorToolsFragment.EditorTools.NONE);
        }
    }

    @Override
    public void onSelectionUpdate(List<MissionItemProxy> selected) {
        super.onSelectionUpdate(selected);
        editorToolsFragment.clearSelected.setEnabled(!selected.isEmpty());
    }

    @Override
    public EditorToolsFragment.EditorTools getEditorTools() {
        return EditorToolsFragment.EditorTools.TRASH;
    }

    @Override
    public void setup() {
        EditorToolsFragment.EditorToolListener listener = editorToolsFragment.listener;
        if (listener != null) {
            listener.enableGestureDetection(false);
        }

        if (missionProxy != null) {
            List<MissionItemProxy> selected = missionProxy.selection.getSelected();
            editorToolsFragment.clearSelected.setEnabled(!selected.isEmpty());

            final List<MissionItemProxy> missionItems = missionProxy.getItems();
            editorToolsFragment.clearMission.setEnabled(!missionItems.isEmpty());
        }

        if (editorToolsFragment.clearVehicleMission != null) {
            editorToolsFragment.clearVehicleMission.setEnabled(isDroneConnected());
        }
    }

    private boolean isDroneConnected() {
        final Drone drone = missionProxy == null ? null : missionProxy.getDrone();
        return drone != null && drone.isConnected();
    }

    private void doClearMissionConfirmation() {
        if (missionProxy == null || missionProxy.getItems().isEmpty())
            return;

        final Context context = editorToolsFragment.getContext();
        SupportYesNoDialog ynd = SupportYesNoDialog.newInstance(context, CLEAR_MISSION_DIALOG_TAG,
                context.getString(R.string
                        .dlg_clear_mission_title),
                context.getString(R.string.dlg_clear_mission_confirm));

        if (ynd != null) {
            ynd.show(editorToolsFragment.getChildFragmentManager(), CLEAR_MISSION_DIALOG_TAG);
        }
    }

    private void deleteSelectedItems() {
        final Context context = editorToolsFragment.getContext();
        SupportYesNoDialog ynd = SupportYesNoDialog.newInstance(context, CLEAR_SELECTED_DIALOG_TAG,
                context.getString(R.string.delete_selected_waypoints_title),
                context.getString(R.string.delete_selected_waypoints_confirm));

        if (ynd != null) {
            ynd.show(editorToolsFragment.getChildFragmentManager(), CLEAR_SELECTED_DIALOG_TAG);
        }
    }

    private void doClearVehicleMissionConfirmation() {
        final Context context = editorToolsFragment.getContext();
        if (context == null)
            return;

        if (!isDroneConnected()) {
            Toast.makeText(context, R.string.clear_vehicle_not_connected, Toast.LENGTH_LONG).show();
            return;
        }

        SupportYesNoDialog ynd = SupportYesNoDialog.newInstance(context, CLEAR_VEHICLE_DIALOG_TAG,
                context.getString(R.string.dlg_clear_vehicle_title),
                context.getString(R.string.dlg_clear_vehicle_confirm));

        if (ynd != null) {
            ynd.show(editorToolsFragment.getChildFragmentManager(), CLEAR_VEHICLE_DIALOG_TAG);
        }
    }

    private void clearVehicleMission() {
        if (missionProxy != null) {
            missionProxy.clearVehicleMission();
        }
    }

    @Override
    public void onDialogYes(String dialogTag) {
        switch (dialogTag) {
            case CLEAR_SELECTED_DIALOG_TAG:
                if (missionProxy != null) {
                    missionProxy.removeSelection(missionProxy.selection);
                    if (missionProxy.selection.getSelected().isEmpty())
                        editorToolsFragment.setTool(EditorToolsFragment.EditorTools.NONE);
                }
                break;

            case CLEAR_MISSION_DIALOG_TAG:
                if (missionProxy != null) {
                    missionProxy.clear();
                    editorToolsFragment.setTool(EditorToolsFragment.EditorTools.NONE);
                }
                break;

            case CLEAR_VEHICLE_DIALOG_TAG:
                clearVehicleMission();
                break;
        }
    }

    @Override
    public void onDialogNo(String dialogTag) {
        switch (dialogTag) {
            case CLEAR_SELECTED_DIALOG_TAG:
                if (missionProxy != null)
                    missionProxy.selection.clearSelection();
                break;

        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.clear_mission_button:
                doClearMissionConfirmation();
                break;

            case R.id.clear_selected_button:
                deleteSelectedItems();
                break;

            case R.id.clear_vehicle_mission_button:
                doClearVehicleMissionConfirmation();
                break;
        }
    }
}
