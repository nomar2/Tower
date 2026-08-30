package org.droidplanner.android.dialogs

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import org.droidplanner.android.R
import org.droidplanner.android.fragments.actionbar.SelectionListAdapter

/**
 * Created by Fredia Huya-Kouadio on 9/25/15.
 *
 * Was an abstract class instantiated via an anonymous subclass in newInstance();
 * AndroidX FragmentTransaction rejects non-static/anonymous fragment classes
 * ("Fragment null must be a public static class"). Now a concrete class holding
 * the adapter as a transient field.
 */
class SelectionListDialog : DialogFragment(), SelectionListAdapter.SelectionListener {

    private var viewAdapter: SelectionListAdapter<*>? = null

    companion object {
        @JvmStatic
        fun newInstance(viewAdapter: SelectionListAdapter<*>?): SelectionListDialog {
            val dialog = SelectionListDialog()
            dialog.viewAdapter = viewAdapter
            viewAdapter?.setSelectionListener(dialog)
            return dialog
        }
    }

    fun getSelectionsAdapter(): SelectionListAdapter<*>? = viewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.CustomDialogTheme)
        isCancelable = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // The adapter can't survive process death; if we were recreated, just go away.
        if (viewAdapter == null) {
            dismissAllowingStateLoss()
            return null
        }
        return inflater.inflate(R.layout.dialog_selection_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectionsView = view.findViewById<ListView>(R.id.selection_list)
        val adapter = getSelectionsAdapter()
        selectionsView?.adapter = adapter
        if (adapter != null)
            selectionsView?.setSelection(adapter.selection)
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(true)
    }

    override fun onPause() {
        super.onPause()
        dismiss()
    }

    override fun onSelection() {
        dismiss()
    }
}
